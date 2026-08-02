package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSkipStateTest {
    @Test
    fun countdownResetsAndTicksWithoutGoingBelowZero() {
        val state = PlayerSkipState().resetCountdown()

        assertEquals(DefaultSkipSegmentCountdownSeconds, state.countdownSeconds)
        assertEquals(0, (1..DefaultSkipSegmentCountdownSeconds + 2)
            .fold(state) { current, _ -> current.tick() }
            .countdownSeconds)
    }

    @Test
    fun hidingMarksOnlyTheCurrentSegment() {
        assertEquals(
            "episode:opening",
            PlayerSkipState().hide("episode:opening").hiddenSegmentKey,
        )
    }
}
