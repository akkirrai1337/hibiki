package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTransportStateTest {
    @Test
    fun readsCommonTransportSnapshot() {
        val transport = object : PlaybackTransport {
            override fun play() = Unit
            override fun pause() = Unit
            override fun setRate(rate: Float) = Unit
            override fun rate() = 1f
            override fun positionMs() = 12_000L
            override fun durationMs() = 100_000L
            override fun bufferedPositionMs() = 20_000L
            override fun seekToMs(positionMs: Long) = Unit
        }

        assertEquals(
            PlaybackTransportState(12_000L, 100_000L, 20_000L, true),
            transport.readState(),
        )
    }
}
