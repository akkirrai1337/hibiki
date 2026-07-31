package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppBackHandlerStateTest {
    @Test
    fun settingsKeepsSystemBackBridgeEnabledWithoutRouteStack() {
        assertTrue(
            appBackHandlerEnabled(
                selectedTab = AppDestination.SETTINGS,
                currentRoute = AppRoute.TopLevel(AppTopLevelDestination.PROFILE),
                hasOverlay = false,
            ),
        )
    }

    @Test
    fun rootHomeDoesNotEnableBackBridgeWithoutBackState() {
        assertFalse(
            appBackHandlerEnabled(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.TopLevel(AppTopLevelDestination.HOME),
                hasOverlay = false,
            ),
        )
    }

    @Test
    fun nestedRouteEnablesSystemBackBridge() {
        assertTrue(
            appBackHandlerEnabled(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.Details("anime-1"),
                hasOverlay = false,
            ),
        )
    }

    @Test
    fun overlayEnablesSystemBackBridge() {
        assertTrue(
            appBackHandlerEnabled(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.TopLevel(AppTopLevelDestination.HOME),
                hasOverlay = true,
            ),
        )
    }

    @Test
    fun activePlaybackEnablesSystemBackBridge() {
        assertTrue(
            appBackHandlerEnabled(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.Player("source-1", "episode-1"),
                hasOverlay = false,
            ),
        )
    }
}
