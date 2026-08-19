package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressCoordinatorTest {
    @Test
    fun skipsDuplicateLifecycleWrites() {
        val persisted = mutableListOf<PlaybackProgressSnapshot>()
        val coordinator = PlaybackProgressCoordinator(persisted::add)
        val snapshot = PlaybackProgressSnapshot(12_000L, 100_000L)

        coordinator.persistIfChanged(snapshot)
        coordinator.persistIfChanged(snapshot)
        coordinator.persistIfChanged(snapshot.copy(positionMs = 13_000L))

        assertEquals(
            listOf(snapshot, snapshot.copy(positionMs = 13_000L)),
            persisted,
        )
    }

    @Test
    fun persistsCurrentTransportPositionThroughSharedPolicy() {
        val persisted = mutableListOf<PlaybackProgressSnapshot>()
        val coordinator = PlaybackProgressCoordinator(persisted::add)
        val transport = FakePlaybackTransport(position = 12_000L, duration = 100_000L)

        coordinator.persistCurrentPosition(transport)

        assertEquals(listOf(PlaybackProgressSnapshot(12_000L, 100_000L)), persisted)
    }

    private class FakePlaybackTransport(
        private val position: Long,
        private val duration: Long,
    ) : PlaybackTransport {
        override fun play() = Unit
        override fun pause() = Unit
        override fun setRate(rate: Float) = Unit
        override fun rate(): Float = 1f
        override fun positionMs(): Long = position
        override fun durationMs(): Long = duration
        override fun bufferedPositionMs(): Long = position
        override fun seekToMs(positionMs: Long) = Unit
    }
}
