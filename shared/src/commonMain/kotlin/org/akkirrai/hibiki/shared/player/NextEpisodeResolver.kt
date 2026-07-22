package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

fun resolveNextEpisodeNumber(progressItems: List<EpisodeWatchProgress>, episodeCount: Int?): Double? {
    val lastWatched = progressItems.filter(EpisodeWatchProgress::isWatchedToEnd).map(EpisodeWatchProgress::episodeNumber).maxOrNull() ?: return 1.0
    val nextSaved = progressItems.map(EpisodeWatchProgress::episodeNumber).filter { it > lastWatched }.minOrNull()
    if (nextSaved != null) return nextSaved
    val inferred = lastWatched + 1.0
    return if (episodeCount == null || inferred <= episodeCount.toDouble()) inferred else null
}

private fun EpisodeWatchProgress.isWatchedToEnd(): Boolean =
    durationMs > 0L && positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)
