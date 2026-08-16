package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.WatchEpisode

/**
 * Returns whether a transport has reached the end of the current episode.
 *
 * The tolerance matches the existing Android progress semantics: a transport
 * may report its final position a fraction before its exact duration.
 */
fun isPlaybackComplete(
    positionMs: Long,
    durationMs: Long,
    toleranceMs: Long = 1_000L,
): Boolean {
    if (durationMs <= 0L || toleranceMs < 0L) return false
    return positionMs >= (durationMs - toleranceMs).coerceAtLeast(0L)
}

/** Resolves the next episode once, using the same completion policy on every host. */
fun resolveAutoPlayNextEpisode(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    positionMs: Long,
    durationMs: Long,
    autoPlayEnabled: Boolean,
    completionHandled: Boolean,
): WatchEpisode? {
    if (!autoPlayEnabled || completionHandled || !isPlaybackComplete(positionMs, durationMs)) {
        return null
    }
    return resolveAdjacentEpisode(
        episodes = episodes,
        currentEpisodeId = currentEpisodeId,
        currentEpisodeNumber = currentEpisodeNumber,
        offset = 1,
    )
}
