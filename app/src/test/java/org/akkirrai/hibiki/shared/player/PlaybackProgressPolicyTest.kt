package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackProgressPolicyTest {
    @Test
    fun persistsPositivePositionIncludingUnknownDuration() {
        assertEquals(
            PlaybackProgressSnapshot(positionMs = 1_000L, durationMs = 0L),
            resolvePersistablePlaybackProgress(positionMs = 1_000L, durationMs = 0L),
        )
    }

    @Test
    fun ignoresEmptyPosition() {
        assertNull(resolvePersistablePlaybackProgress(positionMs = 0L, durationMs = 100_000L))
        assertNull(resolvePersistablePlaybackProgress(positionMs = -1L, durationMs = 100_000L))
    }
}
