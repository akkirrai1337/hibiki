package org.akkirrai.hibiki.shared.player

fun formatEpisodeDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${secondsPart(minutes)}:${secondsPart(seconds)}"
}

private fun secondsPart(value: Long): String = value.toString().padStart(2, '0')
