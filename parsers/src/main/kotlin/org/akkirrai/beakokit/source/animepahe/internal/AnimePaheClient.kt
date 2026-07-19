package org.akkirrai.beakokit.source.animepahe.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.TitleRating
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap

internal class AnimePaheClient(
    client: HttpClient,
    sessionProvider: ChallengeSessionProvider,
    private val baseUrl: String,
) {
    private val http = AnimePaheHttpClient(client, sessionProvider)
    private val summaries = ConcurrentHashMap<String, AnimeTitle>()

    val capabilities = CatalogCapabilities(
        supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
        supportedFilters = emptySet(),
        features = setOf(CatalogFeature.LATEST_RELEASES),
    )

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val adapted = capabilities.adapt(request)
        val offset = adapted.offset.coerceAtLeast(0)
        val limit = adapted.limit.coerceIn(1, MAX_RESULTS)
        val path = if (adapted.query.isBlank()) "/latest-updated" else "/search"
        val params = if (adapted.query.isBlank()) emptyMap() else mapOf("q" to adapted.query.trim())
        val titles = loadHtmlPages(path, params, offset + limit)
        return titles.drop(offset).take(limit)
    }

    suspend fun latest(limit: Int): List<AnimeTitle> {
        val safeLimit = limit.coerceIn(1, MAX_RESULTS)
        return loadHtmlPages("/latest-updated", emptyMap(), safeLimit).take(safeLimit)
    }

    suspend fun getById(id: String): AnimeTitle {
        val session = sessionId(id)
        val parsed = parseDetails(session, http.get(url("/anime/$session")))
        return merge(summaries[session], parsed).also { summaries[session] = it }
    }

    suspend fun getEpisodes(titleId: String): List<Episode> {
        val session = sessionId(titleId)
        val firstPage = releasePage(session, 1)
        val episodes = mutableListOf<Episode>()
        var page = 1
        var root = firstPage
        do {
            root.array("data").orEmpty().mapNotNullTo(episodes) { element ->
                episodeFromRelease(element as? JsonObject ?: return@mapNotNullTo null, session)
            }
            page += 1
            if (page <= (root.int("last_page") ?: 1)) root = releasePage(session, page)
        } while (page <= (root.int("last_page") ?: 1))
        return episodes.distinctBy(Episode::id).sortedBy(Episode::number)
    }

    suspend fun getPlayerLinks(episode: Episode): List<PlayerLink> {
        val episodeSession = episode.id.substringAfter('/', "").takeIf(SESSION_ID::matches) ?: return emptyList()
        val endpoint = url("/anime/get-servers/$episodeSession")
        val root = jsonObject(http.get(endpoint) {
            header(HttpHeaders.Referrer, url("/play/${episode.id}"))
            header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
        })
        return root.array("servers").orEmpty().mapNotNull { element ->
            val server = element as? JsonObject ?: return@mapNotNull null
            val name = server.string("name") ?: return@mapNotNull null
            if (!name.startsWith("Dub-", ignoreCase = true)) return@mapNotNull null
            val playerUrl = server.string("url") ?: return@mapNotNull null
            PlayerLink(
                url = playerUrl,
                type = PlayerType.EMBED,
                quality = server.string("resolution")?.takeUnless { it.equals("Multi", ignoreCase = true) },
                headers = mapOf(HttpHeaders.Referrer to endpoint),
                playerName = name.substringAfter('-').ifBlank { name },
                translation = "English dub",
            )
        }.distinctBy(PlayerLink::url)
    }

    private suspend fun loadHtmlPages(
        path: String,
        params: Map<String, String>,
        wanted: Int,
    ): List<AnimeTitle> {
        val results = LinkedHashMap<String, AnimeTitle>()
        var page = 1
        var hasNext = true
        while (results.size < wanted && hasNext) {
            val pageParams = LinkedHashMap(params).apply { if (page > 1) put("page", page.toString()) }
            val pageHtml = try {
                http.get(pageUrl(path, pageParams))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (results.isEmpty()) throw error
                break
            }
            val document = Jsoup.parse(pageHtml, baseUrl)
            parseCards(document).forEach { title ->
                results[title.id] = merge(results[title.id] ?: summaries[title.id], title)
                summaries[title.id] = results.getValue(title.id)
            }
            hasNext = document.select("a[href]").any { link ->
                link.absUrl("href").let { href ->
                    href.contains(path) && href.contains("page=${page + 1}")
                }
            }
            page += 1
        }
        return results.values.toList()
    }

    private fun parseCards(document: Document): List<AnimeTitle> = document.select(".anime-item")
        .mapNotNull { item ->
            if (item.selectFirst(".lang-dub") == null) return@mapNotNull null
            val link = item.selectFirst(".anime-name a[href], a.anime-poster[href]") ?: return@mapNotNull null
            val id = link.absUrl("href").substringAfterLast('/').substringBefore('?').takeIf(SESSION_ID::matches)
                ?: return@mapNotNull null
            val name = item.selectFirst(".anime-name a")?.text()?.trim()
                ?: item.selectFirst("img[alt]")?.attr("alt")?.trim()
                ?: return@mapNotNull null
            val score = item.selectFirst(".anime-score")?.text()?.trim()?.toDoubleOrNull()
            AnimeTitle(
                id = id,
                russianName = null,
                englishName = name,
                originalName = name,
                japaneseName = null,
                synonyms = emptyList(),
                year = item.selectFirst(".anime-year")?.text()?.trim()?.toIntOrNull(),
                type = item.selectFirst(".anime-type")?.text()?.trim(),
                episodeCount = item.selectFirst(".anime-episodes")?.text()?.let(EPISODE_COUNT::find)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull(),
                posterUrl = item.selectFirst(".anime-poster img")?.imageUrl(),
                status = item.selectFirst(".anime-status")?.text()?.trim(),
                description = null,
                ratings = score?.let { listOf(TitleRating("AnimePahe", it)) }.orEmpty(),
            )
        }.distinctBy(AnimeTitle::id)

    private fun parseDetails(id: String, html: String): AnimeTitle {
        val document = Jsoup.parse(html, baseUrl)
        val name = document.selectFirst(".page-detail h1 span, .page-detail h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: summaries[id]?.originalName
            ?: throw SourceException("AnimePahe title is missing for $id", kind = SourceErrorKind.PARSE)
        val info = document.selectFirst(".anime-info")
        val aired = info?.valueAfter("Aired")
        val synonyms = info?.valueAfter("Synonyms")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        return AnimeTitle(
            id = id,
            russianName = null,
            englishName = name,
            originalName = name,
            japaneseName = info?.valueAfter("Japanese") ?: document.selectFirst("h2.japanese")?.text()?.trim(),
            synonyms = synonyms,
            year = YEAR.find(aired.orEmpty())?.value?.toIntOrNull(),
            type = info?.valueAfter("Type"),
            episodeCount = info?.valueAfter("Episode")?.let(EPISODE_COUNT::find)
                ?.groupValues?.getOrNull(1)?.toIntOrNull(),
            posterUrl = document.selectFirst(".anime-poster img")?.imageUrl()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.trim(),
            status = info?.valueAfter("Status"),
            description = document.selectFirst(".anime-synopsis")?.text()?.trim()?.takeIf(String::isNotBlank),
            genres = document.select(".anime-genre a").map { it.text().trim() }.filter(String::isNotBlank),
            studios = info?.select("a[href*=/studio/]")?.map { it.text().trim() }?.filter(String::isNotBlank).orEmpty(),
        )
    }

    private fun episodeFromRelease(item: JsonObject, titleSession: String): Episode? {
        val session = item.string("session") ?: return null
        val number = item.number("episode") ?: return null
        return Episode(id = "$titleSession/$session", number = number, title = item.string("title"))
    }

    private suspend fun releasePage(session: String, page: Int): JsonObject = jsonObject(
        http.get(
            pageUrl(
                "/viewApi",
                mapOf("m" to "release", "id" to session, "sort" to "episode_asc", "page" to page.toString()),
            ),
        ) {
            header(HttpHeaders.Referrer, url("/anime/$session"))
            header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
        },
    )

    private fun merge(summary: AnimeTitle?, details: AnimeTitle): AnimeTitle = details.copy(
        englishName = details.englishName ?: summary?.englishName,
        year = details.year ?: summary?.year,
        type = details.type ?: summary?.type,
        episodeCount = details.episodeCount ?: summary?.episodeCount,
        posterUrl = details.posterUrl ?: summary?.posterUrl,
        status = details.status ?: summary?.status,
        description = details.description ?: summary?.description,
        ratings = details.ratings.ifEmpty { summary?.ratings.orEmpty() },
    )

    private fun Element.valueAfter(label: String): String? = select("p")
        .firstOrNull { it.selectFirst("strong")?.text()?.trim()?.startsWith(label, ignoreCase = true) == true }
        ?.text()
        ?.substringAfter(':', "")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun Element.imageUrl(): String? =
        attr("src").takeIf(String::isNotBlank)
            ?: attr("data-src").takeIf(String::isNotBlank)

    private fun jsonObject(text: String): JsonObject =
        runCatching { JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw SourceException("AnimePahe returned invalid JSON", kind = SourceErrorKind.PARSE)

    private fun JsonObject.string(name: String): String? = get(name)
        ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    private fun JsonObject.int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull
    private fun JsonObject.number(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray

    private fun sessionId(id: String): String = id.trim().trim('/').substringBefore('/')
        .takeIf(SESSION_ID::matches)
        ?: throw SourceException("AnimePahe title id is invalid: $id", kind = SourceErrorKind.NOT_FOUND)

    private fun pageUrl(path: String, params: Map<String, String>): String = URLBuilder(url(path)).apply {
        params.forEach { (name, value) -> parameters.append(name, value) }
    }.buildString()

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    private companion object {
        const val MAX_RESULTS = 50
        val SESSION_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
        val EPISODE_COUNT = Regex("(\\d+)")
        val YEAR = Regex("(?:19|20)\\d{2}")
        const val X_REQUESTED_WITH = "X-Requested-With"
        const val XML_HTTP_REQUEST = "XMLHttpRequest"
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
