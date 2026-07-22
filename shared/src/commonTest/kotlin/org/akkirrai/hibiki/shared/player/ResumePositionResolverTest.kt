package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResumePositionResolverTest {
    @Test
    fun keepsValidPositionAndClearsNearEndPosition() {
        assertEquals(600_000L, resolveResumablePlaybackPosition(600_000L, 1_430_000L))
        assertNull(resolveResumablePlaybackPosition(1_425_000L, 1_430_000L))
    }

    @Test
    fun supportsUnknownDurationAndRejectsZeroPosition() {
        assertEquals(600_000L, resolveResumablePlaybackPosition(600_000L, 0L))
        assertNull(resolveResumablePlaybackPosition(0L, 1_000L))
    }
}
