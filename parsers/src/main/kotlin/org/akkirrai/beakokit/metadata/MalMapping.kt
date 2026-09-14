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

/** The fields every official MAL API request here asks for - it returns only `id`, `title` and
 * `main_picture` unless told otherwise. */
const val MAL_OFFICIAL_FIELDS =
    "id,title,main_picture,alternative_titles,start_date,synopsis,mean,num_scoring_users,media_type," +
        "status,num_episodes,start_season,genres,studios,nsfw,rating"

/** An anime from MAL's official v2 API ([MalOfficialClient]). */
@Serializable
data class MalOfficialAnime(
    val id: Int,
    val title: String? = null,
    @SerialName("main_picture") val mainPicture: MalOfficialPicture? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalOfficialAlternativeTitles? = null,
    @SerialName("start_date") val startDate: String? = null,
    val synopsis: String? = null,
    val mean: Double? = null,
    @SerialName("num_scoring_users") val numScoringUsers: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
    @SerialName("start_season") val startSeason: MalOfficialSeason? = null,
    val genres: List<JikanNamed>? = null,
    val studios: List<JikanNamed>? = null,
    /** "white", "gray" or "black". */
    val nsfw: String? = null,
    /** "g", "pg", "pg_13", "r", "r+" or "rx". */
    val rating: String? = null,
)

@Serializable
data class MalOfficialPicture(val medium: String? = null, val large: String? = null)

@Serializable
data class MalOfficialAlternativeTitles(
    val synonyms: List<String>? = null,
    val en: String? = null,
    val ja: String? = null,
)

@Serializable
data class MalOfficialSeason(val year: Int? = null)

@Serializable
data class MalOfficialNode(val node: MalOfficialAnime? = null)

@Serializable
data class MalOfficialListResponse(val data: List<MalOfficialNode>? = null)

// The official API writes these as snake_case constants, unlike Jikan's display words.
private val OFFICIAL_TYPE_TO_TYPE = mapOf(
    "tv" to "tv",
    "tv_special" to "special",
    "movie" to "movie",
    "ova" to "ova",
    "ona" to "ona",
    "special" to "special",
    "music" to "special",
    "cm" to "special",
    "pv" to "special",
)

private val OFFICIAL_STATUS_TO_STATUS = mapOf(
    "currently_airing" to "ongoing",
    "finished_airing" to "released",
    "not_yet_aired" to "announced",
)

private val OFFICIAL_RATING_LABELS = mapOf(
    "g" to "G",
    "pg" to "PG",
    "pg_13" to "PG-13",
    "r" to "R",
    "r+" to "R+",
    "rx" to "Rx",
)

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

private fun MalOfficialAnime.startYear(): Int? = startSeason?.year ?: startDate?.take(4)?.toIntOrNull()

fun MalOfficialAnime.toExternalMetadata() = ExternalMetadata(
    provider = MetadataProviderId.MAL,
    externalId = id,
    malId = id,
    anilistId = null,
    // `title` is MAL's main (romaji) title.
    romajiName = title.nonBlank(),
    englishName = alternativeTitles?.en.nonBlank(),
    nativeName = alternativeTitles?.ja.nonBlank(),
    synonyms = alternativeTitles?.synonyms.orEmpty().filter(String::isNotBlank),
    description = sanitizeDescription(synopsis),
    posterUrl = mainPicture?.large ?: mainPicture?.medium,
    bannerUrl = null,
    studios = studios.orEmpty().mapNotNull(JikanNamed::name),
    // Unlike Jikan, the official API already folds themes and demographics into `genres`.
    genres = genres.orEmpty().mapNotNull(JikanNamed::name),
    year = startYear(),
    type = mediaType?.lowercase()?.let(OFFICIAL_TYPE_TO_TYPE::get),
    status = status?.lowercase()?.let(OFFICIAL_STATUS_TO_STATUS::get),
    // 0 is how the official API says "not known yet".
    episodeCount = numEpisodes?.takeIf { it > 0 },
    score = mean,
    scoreVotes = numScoringUsers,
    ageRating = rating?.lowercase()?.let(OFFICIAL_RATING_LABELS::get),
    nextEpisodeAt = null,
    isAdult = rating?.lowercase() == "rx" || nsfw == "black",
)

fun MalOfficialAnime.toMatchCandidate() = MatchCandidate(
    externalId = id,
    names = (listOf(title, alternativeTitles?.en, alternativeTitles?.ja) + alternativeTitles?.synonyms.orEmpty())
        .filterNotNull()
        .filter(String::isNotBlank),
    year = startYear(),
    type = mediaType?.lowercase()?.let(OFFICIAL_TYPE_TO_TYPE::get),
)

fun JikanAnime.toMatchCandidate() = MatchCandidate(
    externalId = malId,
    names = (listOf(title, titleEnglish, titleJapanese) + titles.orEmpty().map(JikanTitle::title) + titleSynonyms.orEmpty())
        .filterNotNull()
        .filter(String::isNotBlank),
    year = airedYear(),
    type = mapMalType(type),
)
