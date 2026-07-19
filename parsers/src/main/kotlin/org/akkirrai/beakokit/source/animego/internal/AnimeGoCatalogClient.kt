package org.akkirrai.beakokit.source.animego.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
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
        supportedSorts = setOf(AnimeSearchSort.RELEVANCE),
        supportedFilters = emptySet(),
        features = setOf(CatalogFeature.LATEST_RELEASES),
    )

    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val adapted = capabilities.adapt(request)
        val limit = adapted.limit.coerceIn(1, MAX_RESULTS)
        val html = if (adapted.query.isBlank()) {
            getHtml("/anime")
        } else {
            getHtml("/search/all", "q" to adapted.query.trim())
        }
        return parseCards(html)
            .drop(adapted.offset.coerceAtLeast(0))
            .take(limit)
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

    fun filterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
        sortOptions = listOf(SearchFilterOption("relevance", "relevance")),
        capabilities = capabilities,
    )

    private suspend fun getHtml(path: String, parameter: Pair<String, String>? = null): String {
        val response = client.get("${baseUrl.trimEnd('/')}$path") {
            parameter?.let { (name, value) -> parameter(name, value) }
        }
        if (!response.status.isSuccess()) {
            throw SourceException(
                message = "AnimeGo returned HTTP ${response.status.value}",
                statusCode = response.status.value,
                kind = when (response.status.value) {
                    404 -> SourceErrorKind.NOT_FOUND
                    429 -> SourceErrorKind.RATE_LIMITED
                    in 500..599 -> SourceErrorKind.NETWORK
                    else -> SourceErrorKind.UNKNOWN
                },
            )
        }
        return response.bodyAsText()
    }

    private fun parseCards(html: String): List<AnimeTitle> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select(".ani-grid__item").mapNotNull { card ->
            val link = card.selectFirst(".ani-grid__item-title a[href^=/anime/]")
                ?: return@mapNotNull null
            val slug = animeSlug(link.absUrl("href")) ?: return@mapNotNull null
            val metadata = card.select(".ani-grid__item-genres__link").map { it.text().trim() }
            val russianName = link.attr("title").ifBlank { link.text() }.trim()
            if (russianName.isBlank()) return@mapNotNull null
            val originalName = card.selectFirst(".ani-grid__item-body > .fw-lighter")
                ?.text()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: russianName
            val rating = card.selectFirst(".rating-badge")?.text()?.replace(',', '.')?.toDoubleOrNull()
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
                posterUrl = card.selectFirst(".ani-grid__item-picture img[src]")?.absUrl("src"),
                status = null,
                description = null,
                ratings = rating?.let { listOf(TitleRating("AnimeGo", it)) }.orEmpty(),
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
            posterUrl = schema.string("image"),
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
        val ANIME_SLUG = Regex("[a-z0-9][a-z0-9-]*-\\d+")
        val SUPPORTED_SCHEMA_TYPES = setOf("TVSeries", "Movie")
    }
}
