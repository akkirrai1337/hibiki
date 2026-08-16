package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SeekDeltaLabelTest {
    @Test
    fun formatsForwardAndBackwardSeekDelta() {
        assertEquals("+00:10", formatSeekDeltaLabel(10_000L))
        assertEquals("-01:05", formatSeekDeltaLabel(-65_000L))
    }
}
