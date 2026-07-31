package org.akkirrai.hibiki.desktop

import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.PlaybackTransport
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/** Desktop-native VLCJ session; Compose owns the surrounding shared controls. */
internal class DesktopVlcPlaybackSession(
    playback: PlaybackStream,
) {
    val component = EmbeddedMediaPlayerComponent()
    val transport: PlaybackTransport = VlcPlaybackTransport(component)

    init {
        component.mediaPlayer().media().play(
            playback.streamUrl,
            *playbackOptions(playback).toTypedArray(),
        )
    }

    fun release() {
        component.release()
    }

    fun videoDimensions(): Pair<Int, Int>? = component.mediaPlayer().video().videoDimension()
        ?.let { dimension -> dimension.width to dimension.height }
}

private class VlcPlaybackTransport(
    private val component: EmbeddedMediaPlayerComponent,
) : PlaybackTransport {
    private val player get() = component.mediaPlayer()

    override fun play() = player.controls().play()

    override fun pause() = player.controls().pause()

    override fun rate(): Float = player.status().rate()

    override fun positionMs(): Long = (player.status().position().coerceIn(0f, 1f) * durationMs()).toLong()

    override fun durationMs(): Long = player.status().length().coerceAtLeast(0L)

    override fun bufferedPositionMs(): Long = positionMs()

    override fun seekToMs(positionMs: Long) {
        val duration = durationMs()
        if (duration > 0L) {
            player.controls().setPosition((positionMs.toFloat() / duration).coerceIn(0f, 1f))
        }
    }
}

private fun playbackOptions(playback: PlaybackStream): List<String> = buildList {
    playback.headers["User-Agent"]?.let { add(":http-user-agent=$it") }
    playback.headers["Referer"]?.let { add(":http-referrer=$it") }
}
