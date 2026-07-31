package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShellChromeStateTest {
    @Test
    fun rootHomeShowsBottomBar() {
        assertTrue(appBottomBarVisible(AppDestination.HOME, false, false, false))
    }

    @Test
    fun nestedScreensHideBottomBar() {
        assertFalse(appBottomBarVisible(AppDestination.HOME, true, false, false))
        assertFalse(appBottomBarVisible(AppDestination.HOME, false, true, false))
        assertFalse(appBottomBarVisible(AppDestination.HOME, false, false, true))
    }

    @Test
    fun settingsHidesBottomBar() {
        assertFalse(appBottomBarVisible(AppDestination.SETTINGS, false, false, false))
    }

    @Test
    fun routeDrivenPolicyShowsOnlyTopLevelContent() {
        assertTrue(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.TopLevel(AppTopLevelDestination.HOME),
            ),
        )
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.Player("source-1", "episode-1"),
            ),
        )
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.PROFILE,
                currentRoute = AppRoute.Settings,
            ),
        )
    }
}
