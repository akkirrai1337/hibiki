package org.akkirrai.hibiki.player

/** Platform media bridge consumed by the shared playback controls. */
interface PlaybackTransport {
    fun play()
    fun pause()
    fun setRate(rate: Float)
    fun rate(): Float
    fun positionMs(): Long
    fun durationMs(): Long
    fun bufferedPositionMs(): Long
    fun seekToMs(positionMs: Long)
}
