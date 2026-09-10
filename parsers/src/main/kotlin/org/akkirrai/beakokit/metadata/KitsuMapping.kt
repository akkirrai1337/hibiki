package org.akkirrai.beakokit.metadata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Kitsu's JSON:API response, reshaped into the provider-neutral [ExternalMetadata]. Pure; the client
 * itself is [KitsuClient].
 *
 * Kitsu earns its place for two things neither of the others has together: it carries banner art, an
 * age rating and a next-episode timestamp all at once, and it publishes the AniList and MAL ids for
 * the same title - which is how a name is turned into the other providers' ids without asking their
 * own search endpoints anything.
 */
@Serializable
data class KitsuAnime(
    val id: String,
    val attributes: KitsuAttributes? = null,
    /** Kept as raw JSON: JSON:API linkage is `{ data: [{ type, id }] }` per relationship, and only
     * two of them are read here - typing the rest would be shape for its own sake. */
    val relationships: JsonObject? = null,
)

@Serializable
data class KitsuAttributes(
    val slug: String? = null,
    val synopsis: String? = null,
    val description: String? = null,
    val canonicalTitle: String? = null,
    /** Keyed by language tag - "en", "en_jp" (romaji), "ja_jp", plus whichever others exist. */
    val titles: Map<String, String?>? = null,
    val abbreviatedTitles: List<String>? = null,
    /** Out of 100, as a string. */
    val averageRating: String? = null,
    val userCount: Int? = null,
    val startDate: String? = null,
    /** ISO timestamp of the next episode, on an airing title. */
    val nextRelease: String? = null,
    val ageRating: String? = null,
    val ageRatingGuide: String? = null,
    val subtype: String? = null,
    val status: String? = null,
    val posterImage: KitsuImage? = null,
    val coverImage: KitsuImage? = null,
    val episodeCount: Int? = null,
    val nsfw: Boolean? = null,
)

@Serializable
data class KitsuImage(val original: String? = null, val large: String? = null)

@Serializable
data class KitsuIncluded(
    val id: String,
    val type: String,
    val attributes: KitsuIncludedAttributes? = null,
)

@Serializable
data class KitsuIncludedAttributes(
    /** A category's display name. Kitsu calls it `title`, not `name`. */
    val title: String? = null,
    val externalSite: String? = null,
    val externalId: String? = null,
)

@Serializable
data class KitsuSingleResponse(val data: KitsuAnime? = null, val included: List<KitsuIncluded>? = null)

@Serializable
data class KitsuListResponse(val data: List<KitsuAnime>? = null, val included: List<KitsuIncluded>? = null)

/** The `include` every request asks for: genres live in `categories` (the `genres` relationship is
 * empty on every entry checked), and `mappings` carries the other providers' ids. */
const val KITSU_INCLUDE = "categories,mappings"

private val SUBTYPE_TO_TYPE = mapOf(
    "tv" to "tv",
    "movie" to "movie",
    "ova" to "ova",
    "ona" to "ona",
    "special" to "special",
    "music" to "special",
)

// "tba" and "unreleased" both mean "announced, no date" here.
private val STATUS_TO_STATUS = mapOf(
    "finished" to "released",
    "current" to "ongoing",
    "upcoming" to "announced",
    "tba" to "announced",
    "unreleased" to "announced",
)

fun mapKitsuSubtype(subtype: String?): String? = subtype?.trim()?.lowercase()?.let(SUBTYPE_TO_TYPE::get)

fun mapKitsuStatus(status: String?): String? = status?.trim()?.lowercase()?.let(STATUS_TO_STATUS::get)

/** The included entries this one anime actually points at - a search returns one flat `included`
 * array for every result at once, and only each result's own relationship linkage says which are
 * its. */
private fun KitsuAnime.relatedIncluded(relationship: String, included: List<KitsuIncluded>): List<KitsuIncluded> {
    val linkage = relationships?.get(relationship)?.jsonObject?.get("data") ?: return emptyList()
    val refs = runCatching { linkage.jsonArray }.getOrNull() ?: return emptyList()
    val wanted = refs.mapNotNull { ref ->
        val obj = runCatching { ref.jsonObject }.getOrNull() ?: return@mapNotNull null
        val type = (obj["type"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        "$type:$id"
    }.toSet()
    return included.filter { "${it.type}:${it.id}" in wanted }
}

private fun KitsuAnime.year(): Int? = attributes?.startDate?.take(4)?.toIntOrNull()

private fun KitsuAnime.ageRating(): String? {
    val rating = attributes?.ageRating?.trim()
    val guide = attributes?.ageRatingGuide?.trim()
    if (rating.isNullOrEmpty()) return guide?.ifEmpty { null }
    // Reads as MAL's own does ("PG-13 - Teens 13 or older"), which is what the details screen already
    // shows for a MAL-described title.
    return if (guide.isNullOrEmpty()) rating else "$rating - $guide"
}

fun KitsuAnime.toExternalMetadata(included: List<KitsuIncluded> = emptyList()): ExternalMetadata {
    val mappings = relatedIncluded("mappings", included)
    fun externalIdOn(site: String): Int? =
        mappings.firstOrNull { it.attributes?.externalSite == site }?.attributes?.externalId?.toIntOrNull()

    val kitsuId = id.toIntOrNull() ?: 0
    return ExternalMetadata(
        provider = MetadataProviderId.KITSU,
        externalId = kitsuId,
        kitsuId = kitsuId,
        anilistId = externalIdOn("anilist/anime"),
        malId = externalIdOn("myanimelist/anime"),
        // Kitsu's "en_jp" is the romanized Japanese title, which is what romajiName means everywhere
        // else here; canonicalTitle is usually the same string and stands in when it is absent.
        romajiName = attributes?.titles?.get("en_jp") ?: attributes?.canonicalTitle,
        englishName = attributes?.titles?.get("en") ?: attributes?.titles?.get("en_us"),
        nativeName = attributes?.titles?.get("ja_jp"),
        synonyms = attributes?.abbreviatedTitles.orEmpty(),
        description = sanitizeDescription(attributes?.synopsis ?: attributes?.description),
        posterUrl = attributes?.posterImage?.original ?: attributes?.posterImage?.large,
        bannerUrl = attributes?.coverImage?.original ?: attributes?.coverImage?.large,
        // Kitsu's `productions` relationship cannot be read the way the others can (the endpoint
        // answers 400), so studios stay with the source.
        studios = emptyList(),
        genres = relatedIncluded("categories", included).mapNotNull { it.attributes?.title },
        year = year(),
        type = mapKitsuSubtype(attributes?.subtype),
        status = mapKitsuStatus(attributes?.status),
        episodeCount = attributes?.episodeCount,
        // Out of 100 like AniList's, but as a decimal string.
        score = attributes?.averageRating?.toDoubleOrNull()?.let { it / 10.0 },
        scoreVotes = attributes?.userCount,
        ageRating = ageRating(),
        nextEpisodeAt = attributes?.nextRelease?.let(::isoToEpochSeconds),
        isAdult = attributes?.nsfw == true,
    )
}

fun KitsuAnime.toMatchCandidate() = MatchCandidate(
    externalId = id.toIntOrNull() ?: 0,
    // Every localized title is worth offering the matcher: an English source may well name a title
    // the way Kitsu's Italian or Portuguese entry does, and none of them can produce a false match on
    // their own (the score still needs the year and type to agree).
    names = (
        listOf(attributes?.canonicalTitle) +
            attributes?.titles.orEmpty().values +
            attributes?.abbreviatedTitles.orEmpty()
        ).filterNotNull().filter(String::isNotBlank),
    year = year(),
    type = mapKitsuSubtype(attributes?.subtype),
)

/** Kitsu's own timestamps are ISO-8601; AnimeTitle.nextEpisodeAt is Unix seconds. */
internal fun isoToEpochSeconds(iso: String): Long? =
    runCatching { java.time.Instant.parse(iso).epochSecond }
        .getOrElse { runCatching { java.time.OffsetDateTime.parse(iso).toEpochSecond() }.getOrNull() }

/** Kitsu's mapping records, read the other way round: "which Kitsu title is this MAL id". */
@Serializable
data class KitsuMappingsResponse(val data: List<KitsuMappingRecord>? = null)

@Serializable
data class KitsuMappingRecord(val id: String, val relationships: KitsuMappingRelationships? = null)

@Serializable
data class KitsuMappingRelationships(val item: KitsuMappingItem? = null)

@Serializable
data class KitsuMappingItem(val data: KitsuMappingItemData? = null)

@Serializable
data class KitsuMappingItemData(val id: String? = null, val type: String? = null)
