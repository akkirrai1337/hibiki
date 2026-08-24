package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.AnimeReleaseStatus
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.RelatedAnimeTitle
import org.akkirrai.hibiki.details.model.isAnnouncementStatus
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AniListMatchHints
import org.akkirrai.hibiki.catalog.model.AnimeRating
import org.akkirrai.hibiki.catalog.model.AnimeTrailer
import org.akkirrai.hibiki.catalog.model.RelatedAnime

data class ExternalAnimeStatusLabels(
    val unknown: String,
    val ongoing: String,
    val released: String,
    val announcement: String,
)

fun AnimeTitle.toAppAnime(
    sourceId: SourceId,
    preferEnglish: Boolean,
    statusLabels: ExternalAnimeStatusLabels,
): Anime {
    val status = releaseStatus.toAppStatus(statusLabels)
    return Anime(
        id = sourceScopedId(sourceId, id),
        title = displayName,
        subtitle = resolveAnimeSubtitle(type, year, null),
        episodesLabel = if (isAnnouncementStatus(status)) {
            statusLabels.announcement
        } else {
            resolveEpisodesLabel(
                releasedCount = availableEpisodeCount
                    ?: episodeCount.takeIf { releaseStatus == AnimeReleaseStatus.RELEASED },
                fallbackLabel = null,
                preferEnglish = preferEnglish,
            )
        },
        status = status,
        nextEpisodeAt = nextEpisodeAt,
        posterUrl = posterUrl,
        posterFallbackUrl = posterFallbackUrl,
        description = description,
        genres = genres,
        alternativeTitles = resolveAlternativeTitles(
            primaryTitle = displayName,
            titleCandidates = listOf(russianName, englishName, originalName, japaneseName) + synonyms,
            fallbackTitles = emptyList(),
        ),
        ratings = ratings.map { AnimeRating(it.source, it.value, it.votes) },
        ageRating = ageRating,
        viewCount = viewCount,
        screenshots = screenshots,
        trailer = trailer?.let { AnimeTrailer(it.id, it.site, it.thumbnailUrl, it.sourceUrl) },
        sourceMaterial = sourceMaterial,
        studios = studios,
        similarAnime = similarAnime.map { it.toAppRelatedAnime(sourceId) },
        franchiseAnime = franchiseAnime.map { it.toAppRelatedAnime(sourceId) },
        relatedAnime = relatedAnime.map { it.toAppRelatedAnime(sourceId) },
        releaseDate = formatReleaseDateLabel(year, season, preferEnglish),
        aniListMatchHints = AniListMatchHints(
            russianName = russianName,
            englishName = englishName,
            originalName = originalName,
            japaneseName = japaneseName,
            synonyms = synonyms,
            year = year,
            type = type,
            episodeCount = episodeCount,
        ),
    )
}

private fun AnimeReleaseStatus.toAppStatus(labels: ExternalAnimeStatusLabels): String = when (this) {
    AnimeReleaseStatus.ONGOING -> labels.ongoing
    AnimeReleaseStatus.RELEASED -> labels.released
    AnimeReleaseStatus.ANNOUNCEMENT -> labels.announcement
    AnimeReleaseStatus.UNKNOWN -> labels.unknown
}

private fun RelatedAnimeTitle.toAppRelatedAnime(sourceId: SourceId): RelatedAnime = RelatedAnime(
    id = sourceScopedId(sourceId, id),
    title = title,
    posterUrl = posterUrl,
    type = type,
    year = year,
    episodeCount = episodeCount,
    status = status,
)

private fun sourceScopedId(sourceId: SourceId, id: String): String =
    AnimeKey.parse(id)?.takeIf { it.sourceId == sourceId }?.value
        ?: AnimeKey(sourceId, id).value
