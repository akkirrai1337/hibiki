package org.akkirrai.hibiki.shared.player

/** Platform media bridge consumed by the shared playback controls. */
interface PlaybackTransport {
    fun play()
    fun pause()
    fun rate(): Float
    fun positionMs(): Long
    fun durationMs(): Long
    fun bufferedPositionMs(): Long
    fun seekToMs(positionMs: Long)
}
