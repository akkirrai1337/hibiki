package org.akkirrai.hibiki.shared.player

fun resolvePlaybackDuration(
    playerDurationMs: Long,
    fallbackDurationMs: Long,
): Long = playerDurationMs.takeIf { it > 0L } ?: fallbackDurationMs
