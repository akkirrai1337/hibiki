package org.akkirrai.hibiki.shared.player

import kotlinx.cinterop.ExperimentalForeignApi
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_get_duration_seconds
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_get_buffered_position_seconds
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_get_position_seconds
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_get_rate
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_pause
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_play
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_seek_seconds
import org.akkirrai.hibiki.shared.player.nativebridge.hibiki_av_player_set_rate
import platform.AVFoundation.AVPlayer

@OptIn(ExperimentalForeignApi::class)
internal class IosAvPlayerTransport(
    private val player: AVPlayer,
) : PlaybackTransport {
    override fun play() = hibiki_av_player_play(player)

    override fun pause() = hibiki_av_player_pause(player)

    fun setRate(rate: Float) = hibiki_av_player_set_rate(player, rate)

    override fun rate(): Float = hibiki_av_player_get_rate(player)

    override fun positionMs(): Long = secondsToMillis(hibiki_av_player_get_position_seconds(player))

    override fun durationMs(): Long = secondsToMillis(hibiki_av_player_get_duration_seconds(player))

    override fun bufferedPositionMs(): Long =
        secondsToMillis(hibiki_av_player_get_buffered_position_seconds(player))

    override fun seekToMs(positionMs: Long) {
        hibiki_av_player_seek_seconds(player, positionMs / 1_000.0)
    }

    private fun secondsToMillis(seconds: Double): Long =
        if (seconds.isFinite() && seconds >= 0.0) (seconds * 1_000.0).toLong() else 0L
}
