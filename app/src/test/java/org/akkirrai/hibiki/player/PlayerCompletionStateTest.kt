package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerCompletionStateTest {
    @Test
    fun completionCanBeHandledOnlyOnceUntilReset() {
        val handled = PlayerCompletionState().markHandled()

        assertEquals(true, handled.isHandled)
        assertEquals(PlayerCompletionState(), handled.reset())
    }
}
