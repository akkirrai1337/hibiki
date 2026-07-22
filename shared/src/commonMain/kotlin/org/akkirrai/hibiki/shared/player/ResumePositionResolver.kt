package org.akkirrai.hibiki.shared.player

private const val PLAYBACK_END_WINDOW_MS = 30_000L
private const val PLAYBACK_END_PERCENT = 5L

fun resolveResumablePlaybackPosition(positionMs: Long, durationMs: Long): Long? {
    val position = positionMs.coerceAtLeast(0L).takeIf { it > 0L } ?: return null
    if (durationMs <= 0L) return position
    val duration = durationMs.coerceAtLeast(1L)
    val resetThresholdMs = maxOf(PLAYBACK_END_WINDOW_MS, duration * PLAYBACK_END_PERCENT / 100L)
    return position.takeIf { duration - position > resetThresholdMs }
}
