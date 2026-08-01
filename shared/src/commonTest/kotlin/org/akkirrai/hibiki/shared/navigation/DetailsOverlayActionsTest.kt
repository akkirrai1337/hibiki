package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsOverlayActionsTest {
    @Test
    fun `details overlay change opens and dismisses only the active overlay`() {
        val opened = AppNavigationState().reduceDetailsOverlayChange(
            overlay = AppOverlay.DetailsPosterPreview,
            open = true,
        )
        assertEquals(listOf(AppOverlay.DetailsPosterPreview), opened.overlays)

        val dismissed = opened.reduceDetailsOverlayChange(
            overlay = AppOverlay.DetailsPosterPreview,
            open = false,
        )
        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(
            dismissed,
            dismissed.reduceDetailsOverlayChange(AppOverlay.DetailsTitleSheet, open = false),
        )
    }
}
