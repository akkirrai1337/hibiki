package org.akkirrai.hibiki.core.anilist

import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.hibiki.catalog.model.AniListMatchHints

internal const val ANILIST_MIN_CONFIDENCE = 0.70

/** Fuzzy-matches [hints] against AniList search candidates using the same [TitleMatcher] scoring
 *  already used to match playback providers. Pure, no I/O. */
internal object AniListMatcher {
    fun bestMatch(
        matcher: TitleMatcher,
        hints: AniListMatchHints,
        candidates: List<AniListSearchMedia>,
        minConfidence: Double = ANILIST_MIN_CONFIDENCE,
    ): AniListSearchMedia? {
        val syntheticTitle = AnimeTitle(
            id = "",
            russianName = hints.russianName,
            englishName = hints.englishName,
            originalName = hints.originalName ?: hints.englishName ?: hints.russianName.orEmpty(),
            japaneseName = hints.japaneseName,
            synonyms = hints.synonyms,
            year = hints.year,
            type = hints.type,
            episodeCount = hints.episodeCount,
            posterUrl = null,
            status = null,
            description = null,
        )
        return candidates
            .map { candidate ->
                candidate to matcher.confidence(
                    title = syntheticTitle,
                    candidateNames = listOfNotNull(
                        candidate.title.romaji,
                        candidate.title.english,
                        candidate.title.native,
                    ),
                    candidateYear = candidate.seasonYear,
                    candidateType = candidate.format,
                    candidateEpisodes = candidate.episodes,
                )
            }
            .filter { (_, confidence) -> confidence >= minConfidence }
            .maxByOrNull { (_, confidence) -> confidence }
            ?.first
    }
}
