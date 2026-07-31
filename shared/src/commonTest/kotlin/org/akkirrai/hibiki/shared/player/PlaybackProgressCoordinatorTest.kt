package org.akkirrai.hibiki.shared.player

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
}
