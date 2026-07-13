package org.akkirrai.animeresolver.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.akkirrai.animeresolver.core.MetadataSource
import org.akkirrai.animeresolver.model.AnimeSearchFilterCatalog
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.SearchFilterOption
import org.akkirrai.animeresolver.network.bodyOrThrow
import org.akkirrai.animeresolver.network.resolveUrl

/** Public AniLiberty metadata API: catalog, search, details and filter references. */
class AniLibertyMetadataSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://anilibria.top/api/v1",
) : MetadataSource {
    override val name = "AniLiberty"

    override suspend fun search(query: String): List<AnimeTitle> = search(AnimeSearchRequest(query = query))

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        val response = client.get("$baseUrl/anime/catalog/releases") {
            parameter("page", request.offset / request.limit.coerceAtLeast(1) + 1)
            parameter("limit", request.limit.coerceIn(1, 100))
            request.query.takeIf(String::isNotBlank)?.let { parameter("f[search]", it) }
            request.typeAliases.takeIf(List<String>::isNotEmpty)?.let { parameter("f[types]", it.joinToString(",") { value -> value.uppercase() }) }
            request.yearFrom?.let { parameter("f[years][from_year]", it) }
            request.yearTo?.let { parameter("f[years][to_year]", it) }
            parameter("f[sorting]", request.sort.toAniLibertySorting())
        }
        return response.bodyOrThrow<JsonElement>(name).releaseArray().mapNotNull { (it as? JsonObject)?.let(::toTitle) }
    }

    suspend fun latest(limit: Int = 20): List<AnimeTitle> = client.get("$baseUrl/anime/releases/latest") {
        parameter("limit", limit.coerceIn(1, 100))
    }.bodyOrThrow<JsonElement>(name).releaseArray().mapNotNull { (it as? JsonObject)?.let(::toTitle) }

    /** Releases scheduled for the current week; suitable for a calendar UI. */
    suspend fun weeklySchedule(): List<AniLibertyScheduleEntry> = loadSchedule("week")

    /** Releases scheduled for the current day. */
    suspend fun currentSchedule(): List<AniLibertyScheduleEntry> = loadSchedule("now")

    override suspend fun getById(id: String): AnimeTitle {
        return client.get("$baseUrl/anime/releases/$id")
            .bodyOrThrow<JsonElement>(name)
            .releaseObject()
            ?.let(::toTitle)
            ?: error("AniLiberty returned an invalid release: $id")
    }

    override suspend fun getSearchFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
        sortOptions = AnimeSearchSort.entries.map { SearchFilterOption(it.name.lowercase(), it.name.lowercase()) },
        typeOptions = references("types").map(::referenceOption),
        statusOptions = references("publish-statuses").map(::referenceOption),
        genreOptions = references("genres").map(::referenceOption),
    )

    private suspend fun references(name: String): List<JsonObject> = client.get("$baseUrl/anime/catalog/references/$name")
        .bodyOrThrow<JsonElement>(this.name).releaseArray().mapNotNull { it as? JsonObject }

    private suspend fun loadSchedule(period: String): List<AniLibertyScheduleEntry> = client
        .get("$baseUrl/anime/schedule/$period")
        .bodyOrThrow<JsonElement>(name)
        .releaseArray()
        .mapNotNull { item ->
            val entry = item as? JsonObject ?: return@mapNotNull null
            val release = (entry["release"] as? JsonObject) ?: entry
            val title = toTitle(release) ?: return@mapNotNull null
            AniLibertyScheduleEntry(
                release = title,
                dayOfWeek = release["publish_day"]?.jsonObject?.int("value"),
            )
        }

    private fun referenceOption(value: JsonObject): SearchFilterOption {
        val id = value.string("id") ?: value.string("value").orEmpty()
        return SearchFilterOption(id, value.string("name") ?: value.string("description") ?: id)
    }

    private fun toTitle(value: JsonObject): AnimeTitle? {
        val id = value.string("id") ?: value.string("alias") ?: return null
        val name = value["name"]?.jsonObject ?: return null
        val main = name.string("main") ?: return null
        val poster = value["poster"]?.jsonObject?.string("src")?.let { resolveUrl("https://anilibria.top", it) }
        return AnimeTitle(
            id = id, russianName = main, englishName = name.string("english"), originalName = name.string("english") ?: main,
            japaneseName = null, synonyms = listOfNotNull(name.string("alternative")), year = value.int("year"),
            type = value["type"]?.jsonObject?.string("value"), episodeCount = value.int("episodes_total"), posterUrl = poster,
            status = if (value.bool("is_ongoing") == true) "ongoing" else "released", description = value.string("description"),
            genres = value["genres"].asArray().mapNotNull { it.jsonObject.string("name") ?: it.jsonObject.string("description") },
            ageRating = value["age_rating"]?.jsonObject?.string("label"), season = value["season"]?.jsonObject?.string("value").toSeason(),
        )
    }

    private fun JsonElement?.releaseArray(): List<JsonElement> = when (this) { is JsonArray -> this; is JsonObject -> (this["data"] ?: this["items"] ?: this["response"]).asArray(); else -> emptyList() }
    private fun JsonElement?.releaseObject(): JsonObject? = when (this) { is JsonObject -> (this["data"] as? JsonObject) ?: this; else -> null }
    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.content?.toIntOrNull()
    private fun JsonObject.bool(key: String): Boolean? = get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull()
    private fun String?.toSeason(): Int? = mapOf("winter" to 1, "spring" to 2, "summer" to 3, "autumn" to 4)[this?.lowercase()]
    private fun AnimeSearchSort.toAniLibertySorting() = when (this) { AnimeSearchSort.TITLE -> "NAME_ASC"; AnimeSearchSort.YEAR -> "YEAR_DESC"; AnimeSearchSort.RATING -> "RATING_DESC"; else -> "FRESH_AT_DESC" }
}

data class AniLibertyScheduleEntry(
    val release: AnimeTitle,
    /** ISO-like AniLiberty day index: 1 (Monday) through 7 (Sunday), if supplied. */
    val dayOfWeek: Int?,
)
