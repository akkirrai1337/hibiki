package org.akkirrai.beakokit.source.animevost.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPath
import io.ktor.http.isSuccess
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.SearchFilterOption
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class AnimeVostCatalogClient(
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
        val query = adapted.query.trim()
        if (query.isBlank()) return latest(adapted.limit)
        return parseCards(getHtml("/xfsearch/${query.encodeURLPath()}/"))
            .drop(adapted.offset.coerceAtLeast(0))
            .take(adapted.limit.coerceIn(1, MAX_RESULTS))
    }

    suspend fun latest(limit: Int): List<AnimeTitle> =
        parseCards(getHtml("/")).take(limit.coerceIn(1, MAX_RESULTS))

    suspend fun getById(id: String): AnimeTitle {
        val path = id.trim().takeIf(TITLE_PATH::matches)
            ?: throw SourceException("AnimeVost title id is invalid: $id", kind = SourceErrorKind.NOT_FOUND)
        val title = parseCards(getHtml("/$path"))
            .firstOrNull { it.id == path }
            ?: throw SourceException("AnimeVost title was not found: $id", kind = SourceErrorKind.NOT_FOUND)
        return title
    }

    suspend fun filterCatalog() = AnimeSearchFilterCatalog(
        sortOptions = listOf(SearchFilterOption("relevance", "relevance")),
        capabilities = capabilities,
    )

    private suspend fun getHtml(path: String): String {
        val response = client.get("${baseUrl.trimEnd('/')}$path") {
            // AnimeVost serves a Cloudflare 403 to non-browser-looking catalog requests.
            header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            header(HttpHeaders.Referrer, "${baseUrl.trimEnd('/')}/")
        }
        if (!response.status.isSuccess()) throw SourceException(
            message = "AnimeVost returned HTTP ${response.status.value}",
            statusCode = response.status.value,
            kind = when (response.status.value) {
                404 -> SourceErrorKind.NOT_FOUND
                403, 429 -> SourceErrorKind.UNAVAILABLE
                in 500..599 -> SourceErrorKind.NETWORK
                else -> SourceErrorKind.UNKNOWN
            },
        )
        return response.bodyAsText()
    }

    private fun parseCards(html: String): List<AnimeTitle> = Jsoup.parse(html, baseUrl)
        .select(".shortstory")
        .mapNotNull { card ->
            val link = card.selectFirst(".shortstoryHead h1, .shortstoryHead h2")
                ?.selectFirst("a[href*=/tip/]") ?: return@mapNotNull null
            val id = link.absUrl("href")
                .substringAfter(baseUrl.trimEnd('/'), missingDelimiterValue = "")
                .substringBefore('?')
                .removePrefix("/")
                .takeIf(TITLE_PATH::matches)
                ?: return@mapNotNull null
            val rawName = link.text().trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val names = rawName.substringBefore('[').trim().split('/', limit = 2).map(String::trim)
            val russianName = names.first().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val content = card.selectFirst(".shortstoryContent") ?: card
            val originalName = content.selectFirst("h4")?.text()?.trim()?.takeIf(String::isNotBlank)
            AnimeTitle(
                id = id,
                russianName = russianName,
                englishName = names.getOrNull(1)?.takeIf(String::isNotBlank),
                originalName = originalName ?: names.getOrNull(1) ?: russianName,
                japaneseName = null,
                synonyms = emptyList(),
                year = content.fieldValue("Год выхода")?.take(4)?.toIntOrNull(),
                type = content.fieldValue("Тип")?.toType(),
                episodeCount = EPISODE_COUNT.find(rawName)?.groupValues?.get(1)?.toIntOrNull(),
                posterUrl = content.selectFirst("img.imgRadius")?.absUrl("src")?.takeIf(String::isNotBlank),
                status = null,
                description = content.select("p")
                    .firstOrNull { it.selectFirst("strong") == null && it.text().trim().length >= DESCRIPTION_MIN_LENGTH }
                    ?.text()?.trim(),
                genres = content.fieldValue("Жанр")?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(),
            )
        }.distinctBy(AnimeTitle::id)

    private fun Element.fieldValue(label: String): String? = select("p")
        .firstOrNull { it.selectFirst("strong")?.text()?.removeSuffix(":")?.trim() == label }
        ?.let { paragraph -> paragraph.text().removePrefix(paragraph.selectFirst("strong")!!.text()).trim() }
        ?.takeIf(String::isNotBlank)

    private fun String.toType(): String = when (trim().lowercase()) {
        "тв" -> "tv"
        "ova" -> "ova"
        "ona" -> "ona"
        "полнометражный фильм" -> "movie"
        else -> trim().lowercase()
    }

    private companion object {
        const val MAX_RESULTS = 50
        const val DESCRIPTION_MIN_LENGTH = 40
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        val TITLE_PATH = Regex("tip/[a-z-]+/\\d+-[^/]+\\.html")
        val EPISODE_COUNT = Regex("\\[\\s*\\d+\\s+из\\s+(\\d+)")
    }
}
