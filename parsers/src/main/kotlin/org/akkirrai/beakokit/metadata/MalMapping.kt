package org.akkirrai.beakokit.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jikan's response (MAL's unofficial read-only API - no key, no account), reshaped into the
 * provider-neutral [ExternalMetadata]. Pure; the client itself is [MalClient].
 */
@Serializable
data class JikanAnime(
    @SerialName("mal_id") val malId: Int,
    val titles: List<JikanTitle>? = null,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    @SerialName("title_synonyms") val titleSynonyms: List<String>? = null,
    val synopsis: String? = null,
    val images: JikanImages? = null,
    val type: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    @SerialName("scored_by") val scoredBy: Int? = null,
    val rating: String? = null,
    val year: Int? = null,
    val aired: JikanAired? = null,
    val genres: List<JikanNamed>? = null,
    val themes: List<JikanNamed>? = null,
    val demographics: List<JikanNamed>? = null,
    val studios: List<JikanNamed>? = null,
    /** Jikan serves unapproved, user-submitted entries too; they are frequently duplicates of a real
     * one with worse data. */
    val approved: Boolean? = null,
)

@Serializable
data class JikanTitle(val type: String? = null, val title: String? = null)

@Serializable
data class JikanNamed(val name: String? = null)

@Serializable
data class JikanImages(val jpg: JikanImage? = null, val webp: JikanImage? = null)

@Serializable
data class JikanImage(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null,
)

@Serializable
data class JikanAired(val from: String? = null)

@Serializable
data class JikanSingleResponse(val data: JikanAnime? = null)

@Serializable
data class JikanListResponse(val data: List<JikanAnime>? = null)

// MAL writes these as display words rather than constants, so the map is keyed on the lowercased
// value and anything unrecognised falls back to the source's own.
private val TYPE_TO_TYPE = mapOf(
    "tv" to "tv",
    "tv special" to "special",
    "movie" to "movie",
    "ova" to "ova",
    "ona" to "ona",
    "special" to "special",
    "music" to "special",
    "cm" to "special",
    "pv" to "special",
)

private val STATUS_TO_STATUS = mapOf(
    "currently airing" to "ongoing",
    "finished airing" to "released",
    "not yet aired" to "announced",
)

fun mapMalType(type: String?): String? = type?.trim()?.lowercase()?.let(TYPE_TO_TYPE::get)

fun mapMalStatus(status: String?): String? = status?.trim()?.lowercase()?.let(STATUS_TO_STATUS::get)

private fun JikanAnime.titleOfType(type: String): String? =
    titles.orEmpty().firstOrNull { it.type?.lowercase() == type }?.title

private fun JikanAnime.airedYear(): Int? {
    year?.let { return it }
    // A fair share of entries (specials and older titles especially) leave `year` null and carry the
    // date only on `aired.from`, and the year is half of what separates a sequel from its prequel.
    val from = aired?.from ?: return null
    return from.take(4).toIntOrNull()
}

fun JikanAnime.toExternalMetadata() = ExternalMetadata(
    provider = MetadataProviderId.MAL,
    externalId = malId,
    malId = malId,
    // Jikan publishes no AniList id, so a switch to AniList still has to search by name once -
    // unlike the other direction, where AniList hands over idMal.
    anilistId = null,
    // MAL's "Default" title is the romaji one, which is what this field means everywhere else.
    romajiName = titleOfType("default") ?: title,
    englishName = titleEnglish ?: titleOfType("english"),
    nativeName = titleJapanese ?: titleOfType("japanese"),
    synonyms = titleSynonyms.orEmpty(),
    description = sanitizeDescription(synopsis),
    // WebP first: MAL's CDN serves it at roughly half the bytes of the JPEG for the same poster.
    posterUrl = images?.webp?.largeImageUrl ?: images?.jpg?.largeImageUrl ?: images?.jpg?.imageUrl,
    // MAL has no banner artwork of any kind.
    bannerUrl = null,
    studios = studios.orEmpty().mapNotNull(JikanNamed::name),
    // MAL splits what every source (and AniList) calls genres across three lists - "Fantasy" is a
    // genre, "Isekai" a theme, "Shounen" a demographic - and the app has one genre row to show them.
    genres = (genres.orEmpty() + themes.orEmpty() + demographics.orEmpty()).mapNotNull(JikanNamed::name),
    year = airedYear(),
    type = mapMalType(type),
    status = mapMalStatus(status),
    episodeCount = episodes,
    // Already out of 10, the same scale ExternalMetadata.score uses.
    score = score,
    scoreVotes = scoredBy,
    ageRating = rating,
    // MAL publishes a weekly broadcast slot but no timestamp for the next episode.
    nextEpisodeAt = null,
    isAdult = rating.orEmpty().lowercase().startsWith("rx"),
)

fun JikanAnime.toMatchCandidate() = MatchCandidate(
    externalId = malId,
    names = (listOf(title, titleEnglish, titleJapanese) + titles.orEmpty().map(JikanTitle::title) + titleSynonyms.orEmpty())
        .filterNotNull()
        .filter(String::isNotBlank),
    year = airedYear(),
    type = mapMalType(type),
)
