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
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset

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
        if (query.isBlank()) return latest(offset = adapted.offset, limit = adapted.limit)
        return parseCards(getHtml("/xfsearch/${query.encodeURLPath()}/"))
            .drop(adapted.offset.coerceAtLeast(0))
            .take(adapted.limit.coerceIn(1, MAX_RESULTS))
    }

    suspend fun latest(limit: Int): List<AnimeTitle> = latest(offset = 0, limit = limit)

    private suspend fun latest(offset: Int, limit: Int): List<AnimeTitle> {
        val requestedLimit = limit.coerceIn(1, MAX_RESULTS)
        var page = offset.coerceAtLeast(0) / LISTING_PAGE_SIZE + 1
        var skip = offset.coerceAtLeast(0) % LISTING_PAGE_SIZE
        val result = mutableListOf<AnimeTitle>()
        while (result.size < requestedLimit) {
            val cards = parseCards(getHtml(if (page == 1) "/" else "/page/$page/"))
            if (cards.isEmpty()) break
            result += cards.drop(skip).take(requestedLimit - result.size)
            if (cards.size < LISTING_PAGE_SIZE) break
            page += 1
            skip = 0
        }
        return result.distinctBy(AnimeTitle::id)
    }

    suspend fun getById(id: String): AnimeTitle {
        val path = id.trim().takeIf(TITLE_PATH::matches)
            ?: throw SourceException("AnimeVost title id is invalid: $id", kind = SourceErrorKind.NOT_FOUND)
        val html = getHtml("/$path")
        val title = parseCards(html)
            .firstOrNull { it.id == path }
            ?: parseDetails(path, html)
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
        .select(".shortstory, article.post")
        .mapNotNull { card ->
            val link = card.selectFirst(".shortstoryHead h1, .shortstoryHead h2")
                ?.selectFirst("a[href*=/tip/]")
                ?: card.selectFirst("span > a[href*=/tip/]")
                ?: return@mapNotNull null
            val id = link.absUrl("href")
                .substringAfter(baseUrl.trimEnd('/'), missingDelimiterValue = "")
                .substringBefore('?')
                .removePrefix("/")
                .takeIf(TITLE_PATH::matches)
                ?: return@mapNotNull null
            val rawName = link.text().trim().takeIf(String::isNotBlank)
                ?: card.selectFirst("h2")?.text()?.trim()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val names = rawName.substringBefore('[').trim().split('/', limit = 2).map(String::trim)
            val russianName = names.first().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val content = card.selectFirst(".shortstoryContent") ?: card
            val originalName = content.selectFirst("h4")?.text()?.trim()?.takeIf(String::isNotBlank)
            val categories = content.select(".short-categori a")
            AnimeTitle(
                id = id,
                russianName = russianName,
                englishName = names.getOrNull(1)?.takeIf(String::isNotBlank),
                originalName = originalName ?: names.getOrNull(1) ?: russianName,
                japaneseName = null,
                synonyms = emptyList(),
                year = content.fieldValue("Год выхода")?.take(4)?.toIntOrNull()
                    ?: categories.firstNotNullOfOrNull { YEAR_PATH.find(it.absUrl("href"))?.groupValues?.get(1)?.toIntOrNull() },
                type = content.fieldValue("Тип")?.toType()
                    ?: categories.firstOrNull { it.absUrl("href").contains("/tip/") }?.text()?.toType(),
                episodeCount = EPISODE_COUNT.find(rawName)?.groupValues?.get(1)?.toIntOrNull(),
                posterUrl = content.selectFirst("img.imgRadius")?.absUrl("src")?.takeIf(String::isNotBlank)
                    ?: BACKGROUND_IMAGE.find(card.attr("style"))?.groupValues?.get(1)?.let { image ->
                        URI(baseUrl).resolve(image).toString()
                    },
                status = "ongoing".takeIf {
                    categories.any { category -> category.absUrl("href").contains("/ongoing/") }
                },
                description = content.select("p")
                    .firstOrNull { it.selectFirst("strong") == null && it.text().trim().length >= DESCRIPTION_MIN_LENGTH }
                    ?.text()?.trim(),
                genres = content.fieldValue("Жанр")?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
                    ?: categories.filter { it.absUrl("href").contains("/zhanr/") }.map(Element::text),
            )
        }.distinctBy(AnimeTitle::id)

    private fun parseDetails(path: String, html: String): AnimeTitle? {
        val document = Jsoup.parse(html, baseUrl)
        val rawName = document.selectFirst(".playerTitle h1, h1")?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        val names = rawName.substringBefore('[').trim().split('/', limit = 2).map(String::trim)
        val russianName = names.first().takeIf(String::isNotBlank) ?: return null
        val progress = EPISODE_PROGRESS.find(rawName)
        val availableEpisodes = progress?.groupValues?.getOrNull(1)?.toIntOrNull()
        val totalEpisodes = progress?.groupValues?.getOrNull(2)?.toIntOrNull()
        val nextEpisodeAt = parseNextEpisodeAt(rawName)
        return AnimeTitle(
            id = path,
            russianName = russianName,
            englishName = names.getOrNull(1)?.takeIf(String::isNotBlank),
            originalName = names.getOrNull(1)?.takeIf(String::isNotBlank) ?: russianName,
            japaneseName = null,
            synonyms = emptyList(),
            year = null,
            type = path.substringAfter("tip/").substringBefore('/').toType(),
            episodeCount = totalEpisodes,
            posterUrl = document.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { image -> URI(baseUrl).resolve(image).toString() },
            status = when {
                nextEpisodeAt != null -> "ongoing"
                availableEpisodes != null && totalEpisodes != null && availableEpisodes < totalEpisodes -> "ongoing"
                totalEpisodes != null -> "released"
                else -> null
            },
            description = document.selectFirst("meta[property='og:description']")?.attr("content")?.trim()?.takeIf(String::isNotBlank),
            nextEpisodeAt = nextEpisodeAt,
            availableEpisodeCount = availableEpisodes,
        )
    }

    private fun parseNextEpisodeAt(rawName: String): Long? {
        val match = NEXT_EPISODE_DATE.find(rawName) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = RUSSIAN_MONTHS[match.groupValues[2].lowercase()] ?: return null
        val today = LocalDate.now(ZoneOffset.UTC)
        val date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        val nextDate = if (date.isBefore(today)) date.plusYears(1) else date
        return nextDate.atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond
    }

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
        const val LISTING_PAGE_SIZE = 10
        const val DESCRIPTION_MIN_LENGTH = 40
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        val TITLE_PATH = Regex("tip/[a-z-]+/\\d+-[^/]+\\.html")
        val EPISODE_COUNT = Regex("\\[\\s*\\d+\\s+из\\s+(\\d+)")
        val EPISODE_PROGRESS = Regex("\\[\\s*(?:\\d+\\s*-\\s*)?(\\d+)\\s+из\\s+(\\d+)")
        val NEXT_EPISODE_DATE = Regex("\\[\\s*\\d+\\s+серия\\s*-\\s*(\\d{1,2})\\s+([а-яё]+)")
        val RUSSIAN_MONTHS = mapOf(
            "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
            "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
            "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
        )
        val YEAR_PATH = Regex("/god/(\\d{4})/")
        val BACKGROUND_IMAGE = Regex("background-image:\\s*url\\(['\"]?([^'\")]+)")
    }
}
