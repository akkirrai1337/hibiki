package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination

class PlayerOverlayActionsTest {
    @Test
    fun dismissCallbacksEmitEventBeforeControlsVisibility() {
        val events = mutableListOf<String>()

        dispatchPlayerPlaylistDismiss(
            setControlsVisible = { events += "controls" },
            onOverlayEvent = { events += "playlist" },
        )
        dispatchPlayerSettingsDismiss(
            setControlsVisible = { events += "controls" },
            onOverlayEvent = { events += "settings" },
        )

        assertEquals(listOf("playlist", "controls", "settings", "controls"), events)
    }

    @Test
    fun lockAndUnlockCallbacksPreservePlayerOrder() {
        val events = mutableListOf<String>()

        dispatchPlayerLock(
            setLocked = { events += "locked" },
            setControlsHidden = { events += "hidden" },
            playlistVisible = true,
            settingsVisible = true,
            onOverlayEvent = { event ->
                events += when (event) {
                    AppNavigationEvent.DismissOverlay -> "playlist"
                    AppNavigationEvent.ClosePlayerSettings -> "settings"
                    else -> error("Unexpected event: $event")
                }
            },
        )
        dispatchPlayerUnlock(
            setUnlocked = { events += "unlocked" },
            setControlsVisible = { events += "visible" },
        )

        assertEquals(listOf("locked", "hidden", "playlist", "settings", "unlocked", "visible"), events)
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
