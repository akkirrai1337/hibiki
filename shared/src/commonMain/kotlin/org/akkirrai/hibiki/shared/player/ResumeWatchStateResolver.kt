package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.TitleWatchState

fun resolveResumeWatchState(progressItems: List<EpisodeWatchProgress>): TitleWatchState? {
    val latest = progressItems
        .asSequence()
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)
        ?: return null
    return TitleWatchState(latest.titleId, latest.episodeId, latest.episodeNumber, latest.sourceId, latest.voiceoverId, latest.sourceTitle, latest.quality, latest.positionMs, latest.durationMs, latest.updatedAt)
}

private fun EpisodeWatchProgress.isWatchedToEnd(): Boolean =
    durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)
