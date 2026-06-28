package org.akkirrai.animeresolver.model

data class AnimeTitle(
    val id: String,
    val russianName: String?,
    val englishName: String?,
    val originalName: String,
    val japaneseName: String?,
    val synonyms: List<String>,
    val year: Int?,
    val type: String?,
    val episodeCount: Int?,
    val posterUrl: String?,
    val status: String?,
    val description: String?,
    val nextEpisodeAt: Long? = null,
    val genres: List<String> = emptyList(),
    val ratings: List<TitleRating> = emptyList(),
    val ageRating: String? = null,
    val viewCount: Long? = null,
    val screenshots: List<String> = emptyList(),
    val sourceMaterial: String? = null,
    val studios: List<String> = emptyList(),
    val mainCharacters: List<CharacterTitle> = emptyList(),
    val similarAnime: List<RelatedAnimeTitle> = emptyList(),
    val franchiseAnime: List<RelatedAnimeTitle> = emptyList(),
    val relatedAnime: List<RelatedAnimeTitle> = emptyList(),
    val season: Int? = null,
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
}

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

data class AnimeSearchFilterCatalog(
    val sortOptions: List<SearchFilterOption> = emptyList(),
    val typeOptions: List<SearchFilterOption> = emptyList(),
    val statusOptions: List<SearchFilterOption> = emptyList(),
    val genreOptions: List<SearchFilterOption> = emptyList(),
)

data class SearchFilterOption(
    val id: String,
    val title: String,
)

enum class AnimeSearchSort {
    RELEVANCE,
    RATING,
    TITLE,
    YEAR,
    VOTES,
    VIEWS,
    COMMENTS,
}

data class TitleRating(
    val source: String,
    val value: Double,
    val votes: Int? = null,
)

data class CharacterTitle(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
)

data class RelatedAnimeTitle(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
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

data class ProviderFailure(
    val providerName: String,
    val message: String,
    val statusCode: Int?,
)

data class SourceDiscovery(
    val matches: List<ProviderMatch>,
    val failures: List<ProviderFailure>,
)

data class Episode(
    val id: String,
    val number: Double,
    val title: String?,
)

enum class PlayerType {
    DIRECT_HLS,
    DIRECT_MP4,
    EMBED,
}

data class PlayerLink(
    val url: String,
    val type: PlayerType,
    val quality: String?,
    val headers: Map<String, String> = emptyMap(),
    val playerName: String? = null,
    val translation: String? = null,
    val segments: List<VideoSegment> = emptyList(),
)

enum class StreamType {
    HLS,
    MP4,
    DASH,
}

data class VideoStream(
    val url: String,
    val type: StreamType,
    val quality: String?,
    val headers: Map<String, String> = emptyMap(),
    val segments: List<VideoSegment> = emptyList(),
)

data class VideoSegment(
    val type: VideoSegmentType,
    val startMs: Long,
    val endMs: Long,
)

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
