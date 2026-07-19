package org.akkirrai.beakokit.source.animego.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.http.pathOf
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.model.TitleRating
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal class AnimeGoCatalogClient(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    val capabilities = CatalogCapabilities(
        supportedSorts = setOf(
            AnimeSearchSort.RELEVANCE,
            AnimeSearchSort.YEAR,
            AnimeSearchSort.RATING,
        ),
        supportedFilters = setOf(
            AnimeSearchFilter.TYPE,
            AnimeSearchFilter.STATUS,
            AnimeSearchFilter.INCLUDED_GENRES,
            AnimeSearchFilter.EXCLUDED_GENRES,
            AnimeSearchFilter.YEAR_RANGE,
        ),
        features = setOf(CatalogFeature.LATEST_RELEASES),
    )

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val adapted = capabilities.adapt(request)
        val limit = adapted.limit.coerceIn(1, MAX_RESULTS)
        if (adapted.query.isNotBlank()) {
            return parseCards(getHtml("/search/all", "q" to adapted.query.trim()))
                .drop(adapted.offset.coerceAtLeast(0))
                .take(limit)
        }
        return collectCatalogPages(adapted, limit)
    }

    suspend fun latest(limit: Int): List<AnimeTitle> =
        parseCards(getHtml("/anime")).take(limit.coerceIn(1, MAX_RESULTS))

    suspend fun getById(id: String): AnimeTitle {
        val slug = id.trim().takeIf(ANIME_SLUG::matches)
            ?: throw SourceException(
                message = "AnimeGo title id is invalid: $id",
                kind = SourceErrorKind.NOT_FOUND,
            )
        return parseDetails(slug, getHtml("/anime/$slug"))
    }

    suspend fun filterCatalog(): AnimeSearchFilterCatalog {
        val document = Jsoup.parse(getHtml("/anime"), baseUrl)
        return AnimeSearchFilterCatalog(
            sortOptions = listOf(
                SearchFilterOption("relevance", "date added"),
                SearchFilterOption("year", "newest"),
                SearchFilterOption("rating", "rating"),
            ),
            typeOptions = document.filterOptions("type_"),
            statusOptions = document.filterOptions("status_"),
            genreOptions = document.filterOptions("genres_")
                .filterNot { it.id.startsWith('!') },
            capabilities = capabilities,
        )
    }

    private suspend fun collectCatalogPages(request: AnimeSearchRequest, limit: Int): List<AnimeTitle> {
        val firstPage = request.offset.coerceAtLeast(0) / PAGE_SIZE + 1
        var page = firstPage
        var skip = request.offset.coerceAtLeast(0) % PAGE_SIZE
        val result = mutableListOf<AnimeTitle>()
        while (result.size < limit) {
            val response = getCatalogPage(request, page)
            val cards = parseCards(response.html)
            result += cards.drop(skip).take(limit - result.size)
            skip = 0
            if (response.endPage || cards.isEmpty()) break
            page++
        }
        return result.distinctBy(AnimeTitle::id).take(limit)
    }

    private suspend fun getCatalogPage(request: AnimeSearchRequest, page: Int): CatalogPage {
        val basePath = request.toFilterPath()
        val path = if (page == 1) basePath else "$basePath/$page"
        val (sort, direction) = request.sort.toAnimeGoSort()
        val response = client.get("${baseUrl.trimEnd('/')}$path") {
            header("X-Requested-With", "XMLHttpRequest")
            parameter("entities", "true")
            parameter("sort", sort)
            parameter("direction", direction)
        }
        if (!response.status.isSuccess()) throw response.toSourceException()
        val body = response.bodyAsText()
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val data = root?.get("data") as? JsonObject
        return CatalogPage(
            html = data?.string("content") ?: body,
            endPage = data?.get("endPage")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
        )
    }

    private suspend fun getHtml(path: String, parameter: Pair<String, String>? = null): String {
        val response = client.get("${baseUrl.trimEnd('/')}$path") {
            parameter?.let { (name, value) -> parameter(name, value) }
        }
        if (!response.status.isSuccess()) throw response.toSourceException()
        return response.bodyAsText()
    }

    private fun parseCards(html: String): List<AnimeTitle> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select(".ani-grid__item, .ani-list__item").mapNotNull { card ->
            val link = card.selectFirst(
                ".ani-grid__item-title a[href^=/anime/], .ani-list__item-title a[href^=/anime/]",
            )
                ?: return@mapNotNull null
            val slug = animeSlug(link.absUrl("href")) ?: return@mapNotNull null
            val metadata = card.select(
                ".ani-grid__item-genres__link, .ani-list__item-genres__link",
            ).map { it.text().trim() }
            val russianName = link.attr("title").ifBlank { link.text() }.trim()
            if (russianName.isBlank()) return@mapNotNull null
            val originalName = card.selectFirst(
                ".ani-grid__item-body > .fw-lighter, .ani-list__item-body > .fw-lighter",
            )
                ?.text()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: russianName
            val rating = card.selectFirst(".rating-badge")?.text()?.replace(',', '.')?.toDoubleOrNull()
            val sourcePosterUrl = card.selectFirst(
                ".ani-grid__item-picture img[src], .ani-list__item-picture img[src]",
            )?.absUrl("src")
            val posterUrl = sourcePosterUrl?.toPosterProxyUrl() ?: sourcePosterUrl
            AnimeTitle(
                id = slug,
                russianName = russianName,
                englishName = originalName.takeIf { it != russianName },
                originalName = originalName,
                japaneseName = null,
                synonyms = emptyList(),
                year = metadata.firstNotNullOfOrNull(String::toIntOrNull),
                type = metadata.firstOrNull()?.toAnimeType(),
                episodeCount = null,
                posterUrl = posterUrl,
                status = null,
                description = null,
                ratings = rating?.let { listOf(TitleRating("AnimeGo", it)) }.orEmpty(),
                posterFallbackUrl = sourcePosterUrl?.takeIf { it != posterUrl },
            )
        }.distinctBy(AnimeTitle::id)
    }

    private fun parseDetails(id: String, html: String): AnimeTitle {
        val document = Jsoup.parse(html, baseUrl)
        val schema = document.select("script[type=application/ld+json]")
            .asSequence()
            .mapNotNull { script -> runCatching { Json.parseToJsonElement(script.data()) as? JsonObject }.getOrNull() }
            .firstOrNull { it.string("@type") in SUPPORTED_SCHEMA_TYPES }
            ?: throw SourceException(
                message = "AnimeGo details schema is missing for $id",
                kind = SourceErrorKind.PARSE,
            )
        val name = document.selectFirst("h1")?.text()?.trim().orEmpty()
        val originalName = schema.string("alternateName") ?: schema.string("name") ?: name
        if (name.isBlank() || originalName.isBlank()) {
            throw SourceException(
                message = "AnimeGo details title is missing for $id",
                kind = SourceErrorKind.PARSE,
            )
        }
        val rating = schema.obj("aggregateRating")
        val episodeText = document.fieldValue("Эпизоды")
        val sourcePosterUrl = schema.string("image")
        val posterUrl = sourcePosterUrl?.toPosterProxyUrl() ?: sourcePosterUrl
        return AnimeTitle(
            id = id,
            russianName = name,
            englishName = originalName.takeIf { it != name },
            originalName = originalName,
            japaneseName = null,
            synonyms = document.select(".entity__title-synonyms li")
                .map(Element::text)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct(),
            year = schema.string("datePublished")?.take(4)?.toIntOrNull(),
            type = schema.string("@type").toAnimeType(),
            episodeCount = schema.int("numberOfEpisodes"),
            posterUrl = posterUrl,
            status = document.fieldValue("Статус")?.lowercase()?.toStatusAlias(),
            description = schema.string("description")
                ?: document.selectFirst(".description")?.text()?.trim(),
            genres = schema.strings("genre"),
            ratings = rating?.double("ratingValue")?.let { value ->
                listOf(TitleRating("AnimeGo", value, rating.int("ratingCount")))
            }.orEmpty(),
            ageRating = schema.string("contentRating"),
            sourceMaterial = document.fieldValue("Первоисточник"),
            studios = schema.obj("productionCompany")?.string("name")?.let(::listOf).orEmpty(),
            availableEpisodeCount = episodeText?.substringBefore('/')?.trim()?.toIntOrNull(),
            posterFallbackUrl = sourcePosterUrl?.takeIf { it != posterUrl },
        )
    }

    private fun animeSlug(url: String): String? = pathOf(url)
        .substringAfter("/anime/", missingDelimiterValue = "")
        .substringBefore('/')
        .takeIf(ANIME_SLUG::matches)

    private fun Document.fieldValue(label: String): String? {
        val labelElement = select("div")
            .firstOrNull { it.ownText().trim().equals(label, ignoreCase = true) }
            ?: return null
        return labelElement.nextElementSibling()?.text()?.trim()?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.string(name: String): String? = get(name)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun JsonObject.int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject

    private fun JsonObject.strings(name: String): List<String> = (get(name) as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .orEmpty()

    private fun Document.filterOptions(namePrefix: String): List<SearchFilterOption> =
        select("input[name^=$namePrefix][value]")
            .mapNotNull { input ->
                val id = input.attr("value").trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                val title = input.closest(".form-check")?.selectFirst("label")?.text()?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: input.attr("data-text").trim().takeIf(String::isNotBlank)
                    ?: id
                SearchFilterOption(id, title)
            }
            .distinctBy(SearchFilterOption::id)

    private fun AnimeSearchRequest.toFilterPath(): String {
        val segments = buildList {
            val from = yearFrom
            val to = yearTo
            when {
                from != null && to != null -> add("year-from-$from-to-$to")
                from != null -> add("year-from-$from")
                to != null -> add("year-to-$to")
            }
            (includedGenreAliases + excludedGenreAliases.map { "!${it.removePrefix("!")}" })
                .pathAliases()
                .takeIf(List<String>::isNotEmpty)
                ?.let { add("genres-is-${it.joinToString("-or-")}") }
            typeAliases.pathAliases().takeIf(List<String>::isNotEmpty)
                ?.let { add("type-is-${it.joinToString("-or-")}") }
            statusAliases.pathAliases().takeIf(List<String>::isNotEmpty)
                ?.let { add("status-is-${it.joinToString("-or-")}") }
        }
        return if (segments.isEmpty()) "/anime" else "/anime/filter/${segments.joinToString("/")}/apply"
    }

    private fun List<String>.pathAliases(): List<String> = asSequence()
        .map { it.trim().lowercase() }
        .filter(ANIMEGO_ALIAS::matches)
        .distinct()
        .toList()

    private fun AnimeSearchSort.toAnimeGoSort(): Pair<String, String> = when (this) {
        AnimeSearchSort.YEAR -> "startDate" to "desc"
        AnimeSearchSort.RATING -> "rating" to "desc"
        else -> "createdAt" to "asc"
    }

    private fun String.toPosterProxyUrl(): String? {
        val sourceUrl = trim().takeIf { it.startsWith("https://img.cdngos.com/") } ?: return null
        return URLBuilder(POSTER_PROXY_URL).apply {
            parameters.append("url", sourceUrl)
            parameters.append("w", "500")
            parameters.append("h", "700")
            parameters.append("fit", "cover")
            parameters.append("output", "webp")
        }.buildString()
    }

    private fun io.ktor.client.statement.HttpResponse.toSourceException() = SourceException(
        message = "AnimeGo returned HTTP ${status.value}",
        statusCode = status.value,
        kind = when (status.value) {
            404 -> SourceErrorKind.NOT_FOUND
            429 -> SourceErrorKind.RATE_LIMITED
            in 500..599 -> SourceErrorKind.NETWORK
            else -> SourceErrorKind.UNKNOWN
        },
    )

    private fun String?.toAnimeType(): String? = when (this?.trim()?.lowercase()) {
        "tvseries", "сериал" -> "tv"
        "movie", "фильм" -> "movie"
        "ova" -> "ova"
        "ona" -> "ona"
        "спешл", "special" -> "special"
        else -> this?.trim()?.lowercase()?.takeIf(String::isNotBlank)
    }

    private fun String.toStatusAlias(): String = when {
        contains("онгоинг") -> "ongoing"
        contains("вышел") || contains("заверш") -> "released"
        contains("анонс") -> "announcement"
        else -> this
    }

    private companion object {
        const val MAX_RESULTS = 50
        const val PAGE_SIZE = 20
        const val POSTER_PROXY_URL = "https://images.weserv.nl/"
        val ANIME_SLUG = Regex("[a-z0-9][a-z0-9-]*-\\d+")
        val ANIMEGO_ALIAS = Regex("!?[a-z0-9][a-z0-9+_-]*")
        val SUPPORTED_SCHEMA_TYPES = setOf("TVSeries", "Movie")
    }

    private data class CatalogPage(val html: String, val endPage: Boolean)
}
