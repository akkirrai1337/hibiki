package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.TitleWatchState

fun resolveResumeWatchState(progressItems: List<EpisodeWatchProgress>): TitleWatchState? {
    val latest = progressItems
        .asSequence()
        .filter { it.positionMs > 0L && !it.isWatchedToEnd() }
        .maxByOrNull(EpisodeWatchProgress::updatedAt)
        ?: return null
    return TitleWatchState(latest.titleId, latest.episodeId, latest.episodeNumber, latest.sourceId, latest.voiceoverId, latest.sourceTitle, latest.quality, latest.positionMs, latest.durationMs, latest.updatedAt)
}
