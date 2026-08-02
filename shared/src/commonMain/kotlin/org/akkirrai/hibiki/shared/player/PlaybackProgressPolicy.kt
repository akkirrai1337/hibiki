package org.akkirrai.hibiki.shared.player

data class PlaybackProgressSnapshot(
    val positionMs: Long,
    val durationMs: Long,
)

/** Keeps progress persistence decisions identical across media transports. */
fun resolvePersistablePlaybackProgress(
    positionMs: Long,
    durationMs: Long,
): PlaybackProgressSnapshot? =
    PlaybackProgressSnapshot(positionMs, durationMs).takeIf { it.positionMs > 0L }
