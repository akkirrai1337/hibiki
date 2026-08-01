package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent

class PlayerOverlayActionsTest {
    @Test
    fun lockDismissesPlaylistBeforeSettings() {
        val events = mutableListOf<AppNavigationEvent>()
        dispatchPlayerOverlayDismissalsForLock(true, true, events::add)
        assertEquals(
            listOf(AppNavigationEvent.DismissOverlay, AppNavigationEvent.ClosePlayerSettings),
            events,
        )
    }

    @Test
    fun hiddenOverlaysDoNotEmitDismissals() {
        val events = mutableListOf<AppNavigationEvent>()
        dispatchPlayerOverlayDismissalsForLock(false, false, events::add)
        assertEquals(emptyList(), events)
    }
}
