package org.akkirrai.beakokit.model

import kotlinx.serialization.Serializable

@Serializable
data class AnimeTitle(
    val id: String,
    val originalName: String,
    val russianName: String? = null,
    val englishName: String? = null,
    val japaneseName: String? = null,
    val synonyms: List<String> = emptyList(),
    val year: Int? = null,
    val type: String? = null,
    val episodeCount: Int? = null,
    val posterUrl: String? = null,
    val status: String? = null,
    val description: String? = null,
    /** Unix timestamp in seconds for the next scheduled episode, when the source provides one. */
    val nextEpisodeAt: Long? = null,
    val genres: List<String> = emptyList(),
    val ratings: List<TitleRating> = emptyList(),
    val ageRating: String? = null,
    val viewCount: Long? = null,
    val screenshots: List<String> = emptyList(),
    val trailer: AnimeTrailerTitle? = null,
    val sourceMaterial: String? = null,
    val studios: List<String> = emptyList(),
    val mainCharacters: List<CharacterTitle> = emptyList(),
    val similarAnime: List<RelatedAnimeTitle> = emptyList(),
    val franchiseAnime: List<RelatedAnimeTitle> = emptyList(),
    val relatedAnime: List<RelatedAnimeTitle> = emptyList(),
    val season: Int? = null,
    /** Episodes currently available from this source; differs from the planned total for ongoing titles. */
    val availableEpisodeCount: Int? = null,
    /** Optional source-owned fallback when the primary poster CDN is unavailable to the host. */
    val posterFallbackUrl: String? = null,
) {
    val displayName: String
        get() = russianName?.takeIf(String::isNotBlank)
            ?: englishName?.takeIf(String::isNotBlank)
            ?: originalName

    fun allNames(): List<String> = buildList {
        add(originalName)
        russianName?.let(::add)
        englishName?.let(::add)
        japaneseName?.let(::add)
        addAll(synonyms)
    }.filter(String::isNotBlank).distinct()

    val releaseStatus: AnimeReleaseStatus
        get() = AnimeReleaseStatus.from(status)
}

enum class AnimeReleaseStatus {
    ONGOING,
    RELEASED,
    ANNOUNCEMENT,
    UNKNOWN;

    companion object {
        fun from(rawStatus: String?): AnimeReleaseStatus = when (rawStatus?.trim()?.lowercase()) {
            "ongoing", "is_ongoing", "airing", "currently airing", "releasing", "онгоинг", "выходит", "в эфире" -> ONGOING
            "released", "completed", "finished", "is_not_ongoing",
            "finished airing",
            "вышел", "вышла", "вышло", "завершен", "завершён", "завершено" -> RELEASED
            "announcement", "announced", "not yet aired", "anons", "анонс", "анонсирован", "анонсировано" -> ANNOUNCEMENT
            else -> UNKNOWN
        }
    }
}

@Serializable
data class AnimeTrailerTitle(
    val id: String,
    val site: String,
    val thumbnailUrl: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
data class AnimeSearchRequest(
    val query: String = "",
    val limit: Int = 20,
    val offset: Int = 0,
    val sort: AnimeSearchSort = AnimeSearchSort.RELEVANCE,
    val typeAliases: List<String> = emptyList(),
    val statusAliases: List<String> = emptyList(),
    val includedGenreAliases: List<String> = emptyList(),
    val excludedGenreAliases: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
)

@Serializable
enum class AnimeSearchFilter {
    TYPE,
    STATUS,
    INCLUDED_GENRES,
    EXCLUDED_GENRES,
    YEAR_RANGE,
}

@Serializable
enum class CatalogFeature {
    LATEST_RELEASES,
    SCHEDULE,
}

@Serializable
data class CatalogCapabilities(
    val supportedSorts: Set<AnimeSearchSort>,
    val supportedFilters: Set<AnimeSearchFilter>,
    val features: Set<CatalogFeature> = emptySet(),
    val fallbackSort: AnimeSearchSort = AnimeSearchSort.RELEVANCE,
) {
    init {
        require(supportedSorts.isNotEmpty()) { "A catalog source must support at least one sort" }
        require(fallbackSort in supportedSorts) { "Fallback sort must be supported" }
    }

    fun supports(sort: AnimeSearchSort): Boolean = sort in supportedSorts

    fun supports(filter: AnimeSearchFilter): Boolean = filter in supportedFilters

    fun adapt(request: AnimeSearchRequest): AnimeSearchRequest = request.copy(
        sort = request.sort.takeIf(::supports) ?: fallbackSort,
        typeAliases = request.typeAliases.takeIf { supports(AnimeSearchFilter.TYPE) }.orEmpty(),
        statusAliases = request.statusAliases.takeIf { supports(AnimeSearchFilter.STATUS) }.orEmpty(),
        includedGenreAliases = request.includedGenreAliases
            .takeIf { supports(AnimeSearchFilter.INCLUDED_GENRES) }
            .orEmpty(),
        excludedGenreAliases = request.excludedGenreAliases
            .takeIf { supports(AnimeSearchFilter.EXCLUDED_GENRES) }
            .orEmpty(),
        yearFrom = request.yearFrom.takeIf { supports(AnimeSearchFilter.YEAR_RANGE) },
        yearTo = request.yearTo.takeIf { supports(AnimeSearchFilter.YEAR_RANGE) },
    )

    companion object {
        val FULL = CatalogCapabilities(
            supportedSorts = AnimeSearchSort.entries.toSet(),
            supportedFilters = AnimeSearchFilter.entries.toSet(),
        )
    }
}

@Serializable
data class AnimeSearchFilterCatalog(
    val sortOptions: List<SearchFilterOption> = emptyList(),
    val typeOptions: List<SearchFilterOption> = emptyList(),
    val statusOptions: List<SearchFilterOption> = emptyList(),
    val genreOptions: List<SearchFilterOption> = emptyList(),
    val capabilities: CatalogCapabilities = CatalogCapabilities.FULL,
)

@Deprecated("Use CatalogFeature", ReplaceWith("CatalogFeature"))
typealias MetadataSourceFeature = CatalogFeature

@Deprecated("Use CatalogCapabilities", ReplaceWith("CatalogCapabilities"))
typealias MetadataSourceCapabilities = CatalogCapabilities

@Serializable
data class SearchFilterOption(
    val id: String,
    val title: String,
)

@Serializable
enum class AnimeSearchSort {
    RELEVANCE,
    RATING,
    TITLE,
    YEAR,
    VOTES,
    VIEWS,
    COMMENTS,
}

@Serializable
data class TitleRating(
    val source: String,
    val value: Double,
    val votes: Int? = null,
)

@Serializable
data class CharacterTitle(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
)

@Serializable
data class RelatedAnimeTitle(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val status: String? = null,
)

data class ProviderMatch(
    val providerId: String,
    val providerName: String,
    val mediaId: String,
    val title: String,
    val confidence: Double,
    val year: Int?,
    val type: String?,
    val episodeCount: Int?,
)

@Serializable
data class Episode(
    val id: String,
    val number: Double,
    val title: String?,
)

@Serializable
enum class PlayerType {
    DIRECT_HLS,
    DIRECT_MP4,
    EMBED,
}

@Serializable
data class PlayerLink(
    val url: String,
    val type: PlayerType,
    val quality: String?,
    val headers: Map<String, String> = emptyMap(),
    val playerName: String? = null,
    val translation: String? = null,
    val segments: List<VideoSegment> = emptyList(),
    val videoId: Long? = null,
    /** Optional separately muxed audio rendition already known by the source. */
    val audioUrl: String? = null,
    /** Empty means the audio rendition inherits [headers]. */
    val audioHeaders: Map<String, String> = emptyMap(),
    /** Source-owned subtitle tracks that do not require a player-specific resolver. */
    val subtitles: List<SubtitleTrack> = emptyList(),
)

@Serializable
enum class StreamType {
    HLS,
    MP4,
    DASH,
}

@Serializable
data class VideoStream(
    val url: String,
    val type: StreamType,
    val quality: String?,
    val headers: Map<String, String> = emptyMap(),
    val segments: List<VideoSegment> = emptyList(),
    /** Optional separately muxed audio rendition for the video stream. */
    val audioUrl: String? = null,
    val audioHeaders: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)

/** An external timed-text track associated with a directly playable stream. */
@Serializable
data class SubtitleTrack(
    val url: String,
    val label: String? = null,
    val language: String? = null,
    /** Empty means the track inherits its enclosing video stream's headers. */
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class VideoSegment(
    val type: VideoSegmentType,
    val startMs: Long,
    val endMs: Long,
)

@Serializable
enum class VideoSegmentType {
    OPENING,
    ENDING,
    UNKNOWN,
}

data class StreamValidationResult(
    val success: Boolean,
    val streamType: StreamType,
    val quality: String?,
    val finalUrl: String,
    val statusCode: Int?,
    val message: String,
    val playerName: String? = null,
    val translation: String? = null,
)
