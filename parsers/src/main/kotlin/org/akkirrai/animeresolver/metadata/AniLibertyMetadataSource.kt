package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.core.SourceException
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchFilter
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.MetadataSourceCapabilities
import org.akkirrai.animeresolver.model.MetadataSourceFeature
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.resolveUrl

/** Public AniLiberty metadata API: catalog, search, details and schedules. */
class AniLibertyMetadataSource(
    private val client: HttpClient,
    private val baseUrls: List<String> = DEFAULT_BASE_URLS,
) : MetadataSource {
    constructor(client: HttpClient, baseUrl: String) : this(client, listOf(baseUrl))

    override val name: String = "AniLiberty"
    override val capabilities = MetadataSourceCapabilities(
        supportedSorts = SUPPORTED_SORTS,
        supportedFilters = setOf(
            AnimeSearchFilter.TYPE,
            AnimeSearchFilter.STATUS,
            AnimeSearchFilter.INCLUDED_GENRES,
            AnimeSearchFilter.YEAR_RANGE,
        ),
        features = setOf(
            MetadataSourceFeature.LATEST_RELEASES,
            MetadataSourceFeature.SCHEDULE,
        ),
    )

    override suspend fun search(query: String): List<AnimeTitle> = search(
        AnimeSearchRequest(query = query),
    )

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val adaptedRequest = capabilities.adapt(request)
        val limit = adaptedRequest.limit.coerceIn(1, MAX_PAGE_SIZE)
        val parameters = buildList {
            add("page" to (adaptedRequest.offset.coerceAtLeast(0) / limit + 1))
            add("limit" to limit)
            adaptedRequest.query.trim().takeIf(String::isNotBlank)?.let { add("f[search]" to it) }
            adaptedRequest.typeAliases.normalizedCsv(uppercase = true)?.let { add("f[types]" to it) }
            adaptedRequest.statusAliases.normalizedCsv(uppercase = true)?.let { add("f[publish_statuses]" to it) }
            adaptedRequest.includedGenreAliases.normalizedCsv()?.let { add("f[genres]" to it) }
            adaptedRequest.yearFrom?.let { add("f[years][from_year]" to it) }
            adaptedRequest.yearTo?.let { add("f[years][to_year]" to it) }
            add("f[sorting]" to adaptedRequest.sort.toAniLibertySorting())
        }
        return requestJson("anime/catalog/releases", parameters)
            .releaseArray()
            .mapNotNull { it.asObject()?.let(::toTitle) }
    }

    override suspend fun latest(limit: Int): List<AnimeTitle> = requestJson(
        path = "anime/releases/latest",
        parameters = listOf("limit" to limit.coerceIn(1, MAX_LATEST_PAGE_SIZE)),
    ).releaseArray().mapNotNull { it.asObject()?.let(::toTitle) }

    /** Releases scheduled for the current week; suitable for a calendar UI. */
    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = loadSchedule("week")

    /** Releases scheduled for the current day. */
    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = loadSchedule("now")

    override suspend fun getById(id: String): AnimeTitle {
        val releaseId = id.trim().takeIf(String::isNotBlank)
            ?: throw SourceException("AniLiberty release id is blank")
        return requestJson("anime/releases/$releaseId")
            .releaseObject()
            ?.let(::toTitle)
            ?: throw SourceException("AniLiberty returned an invalid release: $id")
    }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
        sortOptions = SUPPORTED_SORTS.map { SearchFilterOption(it.name.lowercase(), it.name.lowercase()) },
        typeOptions = references("types").map(::referenceOption),
        statusOptions = references("publish-statuses").map(::referenceOption),
        genreOptions = references("genres").map(::referenceOption),
        capabilities = capabilities,
    )

    private suspend fun references(reference: String): List<JsonObject> = requestJson(
        "anime/catalog/references/$reference",
    ).releaseArray().mapNotNull { it.asObject() }

    private suspend fun loadSchedule(period: String): List<AniLibertyScheduleEntry> = requestJson(
        "anime/schedule/$period",
    ).releaseArray().mapNotNull { item ->
        val entry = item.asObject() ?: return@mapNotNull null
        val release = entry["release"].asObject() ?: entry
        val title = toTitle(release) ?: return@mapNotNull null
        AniLibertyScheduleEntry(
            release = title,
            dayOfWeek = release["publish_day"].asObject()?.int("value"),
        )
    }

    private suspend fun requestJson(
        path: String,
        parameters: List<Pair<String, Any>> = emptyList(),
    ): JsonElement = requestFromMirrors { baseUrl ->
        client.get("${baseUrl.trimEnd('/')}/$path") {
            parameters.forEach { (key, value) -> parameter(key, value) }
        }.bodyOrThrow(name)
    }

    private suspend fun <T> requestFromMirrors(request: suspend (String) -> T): T {
        val errors = mutableListOf<Throwable>()
        baseUrls.forEach { baseUrl ->
            runCatching { request(baseUrl) }
                .onSuccess { return it }
                .onFailure(errors::add)
        }
        throw SourceException("AniLiberty API mirrors are unavailable", cause = errors.firstOrNull())
    }

    private fun referenceOption(value: JsonObject): SearchFilterOption {
        val id = value.string("id") ?: value.string("value").orEmpty()
        val title = value.string("name") ?: value.string("description") ?: id
        return SearchFilterOption(id = id, title = title)
    }

    private fun toTitle(value: JsonObject): AnimeTitle? {
        val id = value.string("id") ?: value.string("alias") ?: return null
        val names = value["name"].asObject() ?: return null
        val mainName = names.string("main") ?: return null
        val englishName = names.string("english")
        val poster = value["poster"].asObject()
        val posterPath = poster?.get("optimized").asObject()?.string("src")
            ?: poster?.string("src")
        val availableEpisodeCount = value["episodes"].asArray().size.takeIf { it > 0 }
        return AnimeTitle(
            id = id,
            russianName = mainName,
            englishName = englishName,
            originalName = englishName ?: mainName,
            japaneseName = null,
            synonyms = names.string("alternative").toAlternativeNames(),
            year = value.int("year"),
            type = value["type"].asObject()?.string("value"),
            episodeCount = value.int("episodes_total") ?: availableEpisodeCount,
            posterUrl = posterPath?.let { resolveUrl(PUBLIC_SITE_URL, it) },
            status = when (value.bool("is_ongoing")) {
                true -> "ongoing"
                false -> "released"
                null -> null
            },
            description = value.string("description"),
            genres = value["genres"].asArray().mapNotNull { genre ->
                genre.asObject()?.let { it.string("name") ?: it.string("description") }
            },
            ageRating = value["age_rating"].asObject()?.string("label"),
            season = value["season"].asObject()?.string("value").toSeason(),
            availableEpisodeCount = availableEpisodeCount,
        )
    }

    private fun JsonElement?.releaseArray(): List<JsonElement> = when (this) {
        is JsonArray -> this
        is JsonObject -> (this["data"] ?: this["items"] ?: this["response"]).asArray()
        else -> emptyList()
    }

    private fun JsonElement?.releaseObject(): JsonObject? = when (this) {
        is JsonObject -> this["data"].asObject() ?: this
        else -> null
    }

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? = get(key)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull

    private fun String?.toAlternativeNames(): List<String> = orEmpty()
        .split(',', ';', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    private fun String?.toSeason(): Int? = when (this?.lowercase()) {
        "winter" -> 1
        "spring" -> 2
        "summer" -> 3
        "autumn" -> 4
        else -> null
    }

    private fun List<String>.normalizedCsv(uppercase: Boolean = false): String? = asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { if (uppercase) it.uppercase() else it }
        .distinct()
        .toList()
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(",")

    private fun AnimeSearchSort.toAniLibertySorting(): String = when (this) {
        AnimeSearchSort.RELEVANCE -> "FRESH_AT_DESC"
        AnimeSearchSort.RATING -> "RATING_DESC"
        AnimeSearchSort.YEAR -> "YEAR_DESC"
        else -> throw SourceException("AniLiberty does not support ${name.lowercase()} sorting")
    }

    private companion object {
        const val PUBLIC_SITE_URL = "https://anilibria.top"
        const val DEFAULT_LATEST_LIMIT = 20
        const val MAX_LATEST_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 50
        val SUPPORTED_SORTS = setOf(
            AnimeSearchSort.RELEVANCE,
            AnimeSearchSort.RATING,
            AnimeSearchSort.YEAR,
        )
        val DEFAULT_BASE_URLS = listOf(
            "https://anilibria.top/api/v1",
            "https://api.anilibria.app/api/v1",
        )
    }
}

data class AniLibertyScheduleEntry(
    val release: AnimeTitle,
    /** ISO day index: 1 (Monday) through 7 (Sunday), if supplied. */
    val dayOfWeek: Int?,
)
