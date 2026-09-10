package org.akkirrai.beakokit.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AniList's GraphQL response, reshaped into the provider-neutral [ExternalMetadata]. Pure, so the
 * mapping is testable without touching the network; the client itself is [AniListClient].
 */
@Serializable
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: AniListTitle? = null,
    val synonyms: List<String>? = null,
    val description: String? = null,
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val genres: List<String>? = null,
    val seasonYear: Int? = null,
    val startDate: AniListDate? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val isAdult: Boolean? = null,
    val studios: AniListStudios? = null,
    val nextAiringEpisode: AniListAiringEpisode? = null,
)

@Serializable
data class AniListTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)

@Serializable
data class AniListCoverImage(val extraLarge: String? = null, val large: String? = null)

@Serializable
data class AniListDate(val year: Int? = null)

@Serializable
data class AniListStudios(val nodes: List<AniListStudio>? = null)

@Serializable
data class AniListStudio(val name: String? = null)

@Serializable
data class AniListAiringEpisode(val airingAt: Long? = null)

@Serializable
data class AniListPage(val media: List<AniListMedia>? = null)

@Serializable
data class AniListData(
    @SerialName("Media") val media: AniListMedia? = null,
    @SerialName("Page") val page: AniListPage? = null,
)

@Serializable
data class AniListResponse(val data: AniListData? = null)

/** The field selection every AniList query here asks for. */
const val ANILIST_MEDIA_FIELDS = """
    id
    idMal
    title { romaji english native }
    synonyms
    description
    coverImage { extraLarge large }
    bannerImage
    genres
    seasonYear
    startDate { year }
    format
    status
    episodes
    averageScore
    isAdult
    studios(isMain: true) { nodes { name } }
    nextAiringEpisode { airingAt }
"""

private val FORMAT_TO_TYPE = mapOf(
    "TV" to "tv",
    "TV_SHORT" to "tv",
    "MOVIE" to "movie",
    "OVA" to "ova",
    "ONA" to "ona",
    "SPECIAL" to "special",
    "MUSIC" to "special",
)

// CANCELLED and HIATUS are deliberately absent: AnimeReleaseStatus has no equivalent, and an
// unmapped status falls back to the source's, which at least says something the app can render.
private val MEDIA_STATUS_TO_STATUS = mapOf(
    "RELEASING" to "ongoing",
    "FINISHED" to "released",
    "NOT_YET_RELEASED" to "announced",
)

fun mapAniListFormat(format: String?): String? = format?.let(FORMAT_TO_TYPE::get)

fun mapAniListStatus(status: String?): String? = status?.let(MEDIA_STATUS_TO_STATUS::get)

fun AniListMedia.toExternalMetadata() = ExternalMetadata(
    provider = MetadataProviderId.ANILIST,
    externalId = id,
    anilistId = id,
    malId = idMal,
    romajiName = title?.romaji,
    englishName = title?.english,
    nativeName = title?.native,
    synonyms = synonyms.orEmpty(),
    description = sanitizeDescription(description),
    posterUrl = coverImage?.extraLarge ?: coverImage?.large,
    bannerUrl = bannerImage,
    studios = studios?.nodes.orEmpty().mapNotNull(AniListStudio::name),
    genres = genres.orEmpty(),
    year = seasonYear ?: startDate?.year,
    type = mapAniListFormat(format),
    status = mapAniListStatus(status),
    episodeCount = episodes,
    // AniList scores out of 100; ExternalMetadata.score is out of 10, like every source's own.
    score = averageScore?.let { it / 10.0 },
    // averageScore carries no vote count in this selection, and asking for popularity instead would
    // report something else entirely.
    scoreVotes = null,
    // AniList has no age-rating field at all, only an adult flag - so this always falls back to
    // whatever the source reported.
    ageRating = null,
    // AniList reports airing times in epoch seconds, the same unit AnimeTitle.nextEpisodeAt uses.
    nextEpisodeAt = nextAiringEpisode?.airingAt,
    isAdult = isAdult == true,
)

fun AniListMedia.toMatchCandidate() = MatchCandidate(
    externalId = id,
    names = listOfNotNull(title?.romaji, title?.english, title?.native) + synonyms.orEmpty(),
    year = seasonYear ?: startDate?.year,
    type = mapAniListFormat(format),
)
