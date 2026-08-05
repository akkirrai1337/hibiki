package org.akkirrai.hibiki.shared.player

data class PlaybackTransportState(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long,
    val isPlaying: Boolean,
)

fun PlaybackTransport.readState(): PlaybackTransportState = PlaybackTransportState(
    positionMs = positionMs(),
    durationMs = durationMs(),
    bufferedPositionMs = bufferedPositionMs(),
    isPlaying = rate() > 0f,
)
