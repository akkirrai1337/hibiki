package org.akkirrai.hibiki.shared.details.model

import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.resolveNextEpisodeNumber
import org.akkirrai.hibiki.shared.player.isWatchedToEnd

enum class WatchCtaAction {
    OpenEpisodes,
    OpenPlayer,
}

data class WatchCtaData(
    val action: WatchCtaAction,
    val episodeNumber: Double? = null,
    val remainingMinutes: Long? = null,
)

fun resolveWatchCtaData(
    progress: TitleWatchState?,
    progressItems: List<EpisodeWatchProgress>,
    selectedSource: WatchSource?,
): WatchCtaData {
    if (progress == null || selectedSource == null) {
        return WatchCtaData(action = WatchCtaAction.OpenEpisodes)
    }

    val inProgressEpisode = progressItems
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)
    if (inProgressEpisode != null) {
        val remainingMinutes = ((inProgressEpisode.durationMs - inProgressEpisode.positionMs)
            .coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
        return WatchCtaData(
            action = WatchCtaAction.OpenPlayer,
            episodeNumber = inProgressEpisode.episodeNumber,
            remainingMinutes = remainingMinutes,
        )
    }

    return WatchCtaData(
        action = WatchCtaAction.OpenEpisodes,
        episodeNumber = resolveNextEpisodeNumber(progressItems, selectedSource.episodeCount),
    )
}
