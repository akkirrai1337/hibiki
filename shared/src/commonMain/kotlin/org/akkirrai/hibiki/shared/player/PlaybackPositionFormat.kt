package org.akkirrai.hibiki.shared.player

fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "$hours:${twoDigit(minutes)}:${twoDigit(seconds)}"
    else "${twoDigit(minutes)}:${twoDigit(seconds)}"
}

private fun twoDigit(value: Long): String = value.toString().padStart(2, '0')
