package org.akkirrai.hibiki.shared.player

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
