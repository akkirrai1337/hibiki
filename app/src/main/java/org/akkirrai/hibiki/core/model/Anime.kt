package org.akkirrai.hibiki.core.model

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
    val sourceMaterial: String? = null,
    val studios: List<String> = emptyList(),
    val franchiseAnime: List<RelatedAnime> = emptyList(),
    val relatedAnime: List<RelatedAnime> = emptyList()
)

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
)
