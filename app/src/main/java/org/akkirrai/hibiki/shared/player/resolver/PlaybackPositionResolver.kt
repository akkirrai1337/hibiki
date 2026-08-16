package org.akkirrai.hibiki.shared.player

fun resolveCurrentPlaybackPosition(
    playerPositionMs: Long,
    trackedPositionMs: Long,
    sliderPositionMs: Long,
): Long {
    val currentPlayerPositionMs = playerPositionMs.coerceAtLeast(0L)
    return when {
        currentPlayerPositionMs > 0L -> currentPlayerPositionMs
        trackedPositionMs > 0L -> trackedPositionMs
        else -> sliderPositionMs.coerceAtLeast(0L)
    }
}
