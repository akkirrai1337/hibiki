package org.akkirrai.hibiki.catalog.model

/** Raw naming/matching fields carried from the source's [org.akkirrai.beakokit.model.AnimeTitle]
 *  so AniList lookups can run entirely off [Anime] without re-fetching from the source. */
data class AniListMatchHints(
    val russianName: String?,
    val englishName: String?,
    val originalName: String?,
    val japaneseName: String?,
    val synonyms: List<String>,
    val year: Int?,
    val type: String?,
    val episodeCount: Int?,
)

data class AniListCharacter(
    val name: String,
    val imageUrl: String?,
    val role: String,
    val voiceActorName: String?,
    val voiceActorImageUrl: String?,
)

/** Resolved AniList metadata for one anime, ready to be merged into [Anime]. */
data class AniListEnrichment(
    val anilistId: Int,
    val bannerUrl: String?,
    val averageScore: Int?,
    val characters: List<AniListCharacter>,
    val directors: List<String>,
)
