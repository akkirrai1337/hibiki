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
                hasBackStack = false,
                hasOverlay = false,
                hasActivePlayback = false,
            ),
        )
    }

    @Test
    fun rootHomeDoesNotEnableBackBridgeWithoutBackState() {
        assertFalse(
            appBackHandlerEnabled(
                selectedTab = AppDestination.HOME,
                hasBackStack = false,
                hasOverlay = false,
                hasActivePlayback = false,
            ),
        )
    }
}
