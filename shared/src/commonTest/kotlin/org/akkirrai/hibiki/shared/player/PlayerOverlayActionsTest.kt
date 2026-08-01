package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination

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
        assertEquals(emptyList<AppNavigationEvent>(), events)
    }

    @Test
    fun settingsDestinationMakesControlsVisibleBeforeEvent() {
        val events = mutableListOf<String>()
        val navigation = mutableListOf<AppNavigationEvent>()

        dispatchPlayerSettingsDestination(
            destination = AppPlayerSettingsDestination.Quality,
            setControlsVisible = { events += "controls" },
            onOverlayEvent = navigation::add,
        )

        assertEquals(listOf("controls"), events)
        val expected = listOf<AppNavigationEvent>(
            AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality),
        )
        assertEquals(expected, navigation)
    }
}
