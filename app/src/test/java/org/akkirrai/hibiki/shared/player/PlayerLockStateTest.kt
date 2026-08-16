package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerLockStateTest {
    @Test
    fun `lock shows only the unlock affordance`() {
        assertEquals(
            PlayerLockState(isLocked = true, isUnlockButtonVisible = true),
            PlayerLockState().lock(),
        )
    }

    @Test
    fun unlockRestoresTheInitialState() {
        assertEquals(PlayerLockState(), PlayerLockState().lock().unlock())
    }
}
