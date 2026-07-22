package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.model.TitleWatchState
import org.akkirrai.hibiki.shared.model.WatchSource

fun filterProgressForSource(progressItems: List<EpisodeWatchProgress>, selectedSource: WatchSource?): List<EpisodeWatchProgress> =
    selectedSource?.let { source -> progressItems.filter { it.sourceId == source.sourceId } } ?: progressItems

fun resolveSourceProgress(fallback: TitleWatchState?, progressItems: List<EpisodeWatchProgress>): TitleWatchState? {
    val latest = progressItems.maxByOrNull(EpisodeWatchProgress::updatedAt) ?: return fallback
    return TitleWatchState(latest.titleId, latest.episodeId, latest.episodeNumber, latest.sourceId, latest.voiceoverId, latest.sourceTitle, latest.quality, latest.positionMs, latest.durationMs, latest.updatedAt)
}
