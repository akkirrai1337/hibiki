package org.akkirrai.hibiki.shared.model

data class Anime(
    val id: String,
    val title: String,
    val subtitle: String,
    val episodesLabel: String,
    val status: String,
    val nextEpisodeAt: Long? = null,
    val posterUrl: String? = null,
    val posterFallbackUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val alternativeTitles: List<String> = emptyList(),
    val ratings: List<AnimeRating> = emptyList(),
    val ageRating: String? = null,
    val viewCount: Long? = null,
    val screenshots: List<String> = emptyList(),
    val trailer: AnimeTrailer? = null,
    val sourceMaterial: String? = null,
    val studios: List<String> = emptyList(),
    val similarAnime: List<RelatedAnime> = emptyList(),
    val franchiseAnime: List<RelatedAnime> = emptyList(),
    val relatedAnime: List<RelatedAnime> = emptyList(),
    val releaseDate: String? = null,
)

data class AnimeTrailer(
    val id: String,
    val site: String,
    val thumbnailUrl: String? = null,
    val sourceUrl: String? = null,
) {
    val playbackUrl: String?
        get() = when (site.lowercase()) {
            "youtube" -> "https://www.youtube.com/watch?v=$id"
            else -> sourceUrl
        }
}

data class AnimeRating(
    val source: String,
    val value: Double,
    val votes: Int? = null,
)

data class RelatedAnime(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val posterFallbackUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val status: String? = null,
)
