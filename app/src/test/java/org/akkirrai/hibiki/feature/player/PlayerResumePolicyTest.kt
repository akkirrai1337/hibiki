package org.akkirrai.hibiki.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerResumePolicyTest {
    @Test
    fun `keeps a useful saved position`() {
        assertEquals(
            600_000L,
            resumablePlaybackPositionMs(positionMs = 600_000L, durationMs = 1_430_000L),
        )
    }

    @Test
    fun `starts completed episode from the beginning`() {
        assertNull(
            resumablePlaybackPositionMs(positionMs = 1_425_000L, durationMs = 1_430_000L),
        )
        assertNull(
            resumablePlaybackPositionMs(positionMs = 1_400_000L, durationMs = 1_430_000L),
        )
    }
}
