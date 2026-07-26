package org.akkirrai.beakokit.source.gogoanime.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import java.nio.charset.Charset
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.http.pathOf
import org.akkirrai.beakokit.http.resolveUrl
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.SearchFilterOption
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal data class EpisodeWithCategory(
    val episode: Episode,
    val category: String,
)

internal class GogoAnimeClient(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    val capabilities = CatalogCapabilities(
        supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
        supportedFilters = emptySet(),
        features = setOf(CatalogFeature.LATEST_RELEASES),
    )

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val adapted = capabilities.adapt(request)
        val limit = adapted.limit.coerceIn(1, MAX_RESULTS)
        if (adapted.query.isBlank()) return latest(limit).drop(adapted.offset.coerceAtLeast(0)).take(limit)

        val results = mutableListOf<AnimeTitle>()
        val knownIds = linkedSetOf<String>()
        var page = 1
        var skip = adapted.offset.coerceAtLeast(0)
        while (results.size < limit) {
            val pageResults = parseSearchCards(Jsoup.parse(getHtml("/search", searchParameters(adapted.query, page)), baseUrl))
            if (pageResults.isEmpty()) break
            val newResults = pageResults.filter { knownIds.add(it.id) }
            if (newResults.isEmpty()) break
            if (skip >= newResults.size) {
                skip -= newResults.size
            } else {
                results += newResults.drop(skip).take(limit - results.size)
                skip = 0
            }
            page++
        }
        return results.take(limit)
    }

    suspend fun latest(limit: Int): List<AnimeTitle> =
        parseLatestCards(Jsoup.parse(getHtml("/"), baseUrl))
            .take(limit.coerceIn(1, MAX_RESULTS))

    suspend fun getById(id: String): AnimeTitle {
        val slug = validateSlug(id)
        val document = Jsoup.parse(getHtml("/category/$slug"), baseUrl)
        val episodes = parseEpisodes(document)
        return parseDetails(slug, document, episodes.size)
    }

    suspend fun getEpisodes(id: String): List<EpisodeWithCategory> {
        val slug = validateSlug(id)
        return parseEpisodes(Jsoup.parse(getHtml("/category/$slug"), baseUrl))
    }

    suspend fun getPlayerLinks(episode: Episode, translation: String): List<PlayerLink> {
        val path = episode.id.trim().takeIf { it.startsWith('/') } ?: "/${episode.id.trimStart('/')}"
        val episodeUrl = resolveUrl(baseUrl, path)
        val document = Jsoup.parse(getHtml(path), baseUrl)
        val links: List<PlayerLink?> = buildList {
            document.select("a[data-video]").forEach { element ->
                add(addPlayerLink(element.attr("data-video"), element.text(), episodeUrl, translation))
            }
            document.select(".play-video iframe[src], iframe[src]").forEach { element ->
                add(addPlayerLink(element.attr("src"), element.attr("title"), episodeUrl, translation))
            }
        }
        return links.filterNotNull().distinctBy(PlayerLink::url)
    }

    fun filterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
        sortOptions = listOf(SearchFilterOption("relevance", "Relevance")),
        capabilities = capabilities,
    )

    private suspend fun getHtml(path: String, parameters: Map<String, String> = emptyMap()): String {
        val requestUrl = URLBuilder(resolveUrl(baseUrl, path)).apply {
            parameters.forEach { (name, value) -> this.parameters.append(name, value) }
        }.buildString()
        val response = client.get(requestUrl) {
            header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            header(HttpHeaders.Referrer, "${baseUrl.trimEnd('/')}/")
        }
        if (!response.status.isSuccess()) throw SourceException(
            message = "GogoAnime returned HTTP ${response.status.value}",
            statusCode = response.status.value,
            kind = when (response.status.value) {
                404 -> SourceErrorKind.NOT_FOUND
                403, 429 -> SourceErrorKind.UNAVAILABLE
                in 500..599 -> SourceErrorKind.NETWORK
                else -> SourceErrorKind.UNKNOWN
            },
        )
        return response.bodyAsText().repairGogoMojibake()
    }

    private fun parseSearchCards(document: Document): List<AnimeTitle> = document.select("ul.items > li")
        .mapNotNull { card ->
            val link = card.selectFirst("p.name a[href^=/category/], .img a[href^=/category/]") ?: return@mapNotNull null
            val id = categoryId(link.absUrl("href")) ?: return@mapNotNull null
            val name = link.attr("title").trim().ifBlank { link.text().trim() }.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            AnimeTitle(
                id = id,
                russianName = null,
                englishName = name,
                originalName = name,
                japaneseName = null,
                synonyms = emptyList(),
                year = null,
                type = null,
                episodeCount = null,
                posterUrl = card.selectFirst(".img img")?.absUrl("src")?.takeIf(String::isNotBlank),
                status = null,
                description = null,
            )
        }
        .distinctBy(AnimeTitle::id)

    private fun parseLatestCards(document: Document): List<AnimeTitle> = document
        .select(".last_episodes li, ul.items > li")
        .filter { it.selectFirst(".img img, .thumbnail-recent") != null }
        .mapNotNull { card ->
            val link = card.selectFirst("a[href*=-episode-]") ?: return@mapNotNull null
            val match = EPISODE_PATH.matchEntire(pathOf(link.absUrl("href"))) ?: return@mapNotNull null
            val name = link.attr("title").trim().takeIf(String::isNotBlank)
                ?: card.selectFirst("a[title]")?.attr("title")?.trim()
                ?: return@mapNotNull null
            val poster = card.selectFirst(".img img")?.absUrl("src")?.takeIf(String::isNotBlank)
                ?: BACKGROUND_IMAGE.find(card.selectFirst(".thumbnail-recent")?.attr("style").orEmpty())
                    ?.groupValues?.getOrNull(1)
                    ?.let { resolveUrl(baseUrl, it) }
            AnimeTitle(
                id = match.groupValues[1],
                russianName = null,
                englishName = name,
                originalName = name,
                japaneseName = null,
                synonyms = emptyList(),
                year = null,
                type = null,
                episodeCount = null,
                posterUrl = poster,
                status = null,
                description = null,
                availableEpisodeCount = match.groupValues[2].toIntOrNull(),
            )
        }
        .distinctBy(AnimeTitle::id)

    private fun parseDetails(id: String, document: Document, availableCount: Int): AnimeTitle {
        val info = document.selectFirst(".anime_info_body_bg")
            ?: throw SourceException("GogoAnime details are missing for $id", kind = SourceErrorKind.PARSE)
        val name = info.selectFirst("h1")?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: throw SourceException("GogoAnime title is missing for $id", kind = SourceErrorKind.PARSE)
        val type = info.fieldValue("Type")?.lowercase()?.let { raw ->
            when (raw) {
                "tv" -> "tv"
                "movie" -> "movie"
                "ova" -> "ova"
                "ona" -> "ona"
                else -> raw
            }
        }
        val status = info.fieldValue("Status")?.lowercase()?.let { raw ->
            when {
                "ongoing" in raw || "airing" in raw -> "ongoing"
                "complete" in raw || "finished" in raw -> "released"
                "announce" in raw -> "announcement"
                else -> raw
            }
        }
        val otherNames = info.fieldValue("Other name")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        val synopsis = document.selectFirst(".anime_info_episodes > div p")?.text()?.trim()
        val plot = info.fieldValue("Plot Summary")
        val year = YEAR.find(info.fieldValue("Released").orEmpty())?.value?.toIntOrNull()
        val episodes = availableCount.takeIf { it > 0 }
        val isOngoing = AnimeReleaseStatus.from(status) == AnimeReleaseStatus.ONGOING
        return AnimeTitle(
            id = id,
            russianName = null,
            englishName = name,
            originalName = name,
            japaneseName = otherNames.getOrNull(1),
            synonyms = otherNames,
            year = year,
            type = type,
            episodeCount = episodes.takeUnless { isOngoing },
            posterUrl = info.selectFirst("img")?.absUrl("src")?.takeIf(String::isNotBlank),
            status = status,
            description = synopsis?.takeIf(String::isNotBlank) ?: plot?.takeIf(String::isNotBlank),
            genres = info.select("p.type")
                .firstOrNull { it.selectFirst("span")?.text()?.trim()?.removeSuffix(":")?.equals("Genre", ignoreCase = true) == true }
                ?.select("a")
                ?.map { it.text().trim() }
                ?.filter(String::isNotBlank)
                .orEmpty(),
            availableEpisodeCount = episodes,
        )
    }

    private fun parseEpisodes(document: Document): List<EpisodeWithCategory> = document.select("#episode_related a[href]")
        .mapNotNull { link ->
            val path = pathOf(link.absUrl("href"))
            val number = EPISODE_PATH.matchEntire(path)?.groupValues?.get(2)?.toDoubleOrNull()
                ?: link.selectFirst(".name")?.text()?.filter { it.isDigit() }?.toDoubleOrNull()
                ?: return@mapNotNull null
            val category = link.selectFirst(".cate")?.text()?.trim()?.lowercase().takeUnless { it.isNullOrBlank() }
                ?: "sub"
            EpisodeWithCategory(
                episode = Episode(id = path, number = number, title = null),
                category = category,
            )
        }
        .distinctBy { it.category to it.episode.id }
        .sortedWith(compareBy(EpisodeWithCategory::category, { it.episode.number }))

    private fun addPlayerLink(
        rawUrl: String,
        label: String,
        episodeUrl: String,
        translation: String,
    ): PlayerLink? {
        val url = rawUrl.trim().takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) } ?: return null
        val quality = QUALITY.find(label)?.groupValues?.getOrNull(1)
        return PlayerLink(
            url = url,
            type = PlayerType.EMBED,
            quality = quality,
            headers = mapOf(HttpHeaders.Referrer to episodeUrl),
            playerName = label.trim().takeIf(String::isNotBlank),
            translation = translation,
        )
    }

    private fun Element.fieldValue(label: String): String? = select("p.type")
        .firstOrNull { it.selectFirst("span")?.text()?.trim()?.removeSuffix(":")?.equals(label, ignoreCase = true) == true }
        ?.let { paragraph ->
            paragraph.clone().also { it.select("span").remove() }.text().trim()
        }
        ?.takeIf(String::isNotBlank)

    /** Repairs UTF-8 text that GogoAnime occasionally serves as Windows-1252. */
    private fun String.repairGogoMojibake(): String {
        if (countGogoMojibakeMarkers() == 0) return this
        val originalQuestionMarks = count { it == '?' }
        val repaired = listOf(WINDOWS_1252, Charsets.ISO_8859_1)
            .mapNotNull { charset ->
                runCatching { toByteArray(charset).toString(Charsets.UTF_8) }.getOrNull()
            }
            .filter { candidate ->
                candidate != this &&
                    !candidate.contains('\uFFFD') &&
                    candidate.count { it == '?' } <= originalQuestionMarks
            }
            .minByOrNull { candidate -> candidate.countGogoMojibakeMarkers() }
        return if (repaired != null && repaired.countGogoMojibakeMarkers() < countGogoMojibakeMarkers()) {
            repaired
        } else {
            this
        }
    }

    private fun String.countGogoMojibakeMarkers(): Int = count {
        it in GOGO_MOJIBAKE_MARKERS || it.code in C1_CONTROL_RANGE
    }

    private fun validateSlug(id: String): String = id.trim().trim('/').takeIf(SLUG::matches)
        ?: throw SourceException("GogoAnime title id is invalid: $id", kind = SourceErrorKind.NOT_FOUND)

    private fun categoryId(url: String): String? = pathOf(url)
        .substringAfter("/category/", missingDelimiterValue = "")
        .substringBefore('/')
        .takeIf(SLUG::matches)

    private fun searchParameters(query: String, page: Int): Map<String, String> = buildMap {
        put("keyword", query.trim())
        put("page", page.toString())
    }

    private companion object {
        const val MAX_RESULTS = 50
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        val WINDOWS_1252: Charset = Charset.forName("windows-1252")
        val GOGO_MOJIBAKE_MARKERS = setOf('Ã', 'Â', 'â', 'ð', 'Ð', 'Ñ', 'ã', 'ƒ', '�')
        val C1_CONTROL_RANGE = 0x80..0x9F
        val SLUG = Regex("[a-z0-9][a-z0-9-]*")
        val EPISODE_PATH = Regex("/(.+)-episode-(\\d+(?:\\.\\d+)?)$")
        val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")
        val QUALITY = Regex("\\b(\\d{3,4}p)\\b", RegexOption.IGNORE_CASE)
        val BACKGROUND_IMAGE = Regex("background:\\s*url\\(['\"]?([^'\")]+)", RegexOption.IGNORE_CASE)
    }
}
