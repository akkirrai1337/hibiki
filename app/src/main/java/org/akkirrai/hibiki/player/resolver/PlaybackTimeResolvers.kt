package org.akkirrai.hibiki.player

import kotlin.math.abs

fun formatPlaybackSpeed(speed: Float): String = if (speed == 1f) "1x" else "${speed}x"

fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "$hours:${twoDigit(minutes)}:${twoDigit(seconds)}"
    else "${twoDigit(minutes)}:${twoDigit(seconds)}"
}

private fun twoDigit(value: Long): String = value.toString().padStart(2, '0')

private const val PLAYBACK_END_WINDOW_MS = 30_000L
private const val PLAYBACK_END_PERCENT = 5L

fun resolveResumablePlaybackPosition(positionMs: Long, durationMs: Long): Long? {
    val position = positionMs.coerceAtLeast(0L).takeIf { it > 0L } ?: return null
    if (durationMs <= 0L) return position
    val duration = durationMs.coerceAtLeast(1L)
    val resetThresholdMs = maxOf(PLAYBACK_END_WINDOW_MS, duration * PLAYBACK_END_PERCENT / 100L)
    return position.takeIf { duration - position > resetThresholdMs }
}

fun formatSeekDeltaLabel(deltaMs: Long): String {
    val sign = if (deltaMs >= 0L) "+" else "-"
    return sign + formatEpisodeDuration(abs(deltaMs))
}
