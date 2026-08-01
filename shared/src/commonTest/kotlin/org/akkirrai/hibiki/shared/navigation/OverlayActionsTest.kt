package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayActionsTest {
    @Test
    fun `visibility change preserves inactive dismiss as a no-op`() {
        val state = AppNavigationState()
        assertEquals(
            state,
            state.reduceOverlayVisibilityChange(AppOverlay.Sheet("filter"), visible = false),
        )
    }
}
