package org.akkirrai.hibiki.shared.player

import androidx.media3.exoplayer.ExoPlayer

/** Media3-only bridge for the common playback controls. */
internal class AndroidMedia3PlaybackTransport(
    private val player: ExoPlayer,
) : PlaybackTransport {
    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun setRate(rate: Float) {
        player.setPlaybackSpeed(rate)
    }

    override fun rate(): Float = if (player.isPlaying) {
        player.playbackParameters.speed
    } else {
        0f
    }

    override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    override fun durationMs(): Long = player.duration.takeIf { it > 0L } ?: 0L

    override fun bufferedPositionMs(): Long = player.bufferedPosition.coerceAtLeast(0L)

    override fun seekToMs(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }
}
