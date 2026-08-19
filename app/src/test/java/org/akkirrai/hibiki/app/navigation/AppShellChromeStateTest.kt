package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShellChromeStateTest {
    @Test
    fun rootHomeShowsBottomBar() {
        assertTrue(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.TopLevel(AppTopLevelDestination.HOME),
            ),
        )
    }

    @Test
    fun titleDetailsHideBottomBarAfterTransition() {
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.Details("anime-1"),
            ),
        )
    }

    @Test
    fun watchScreensHideBottomBar() {
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.WatchSources("anime-1"),
            ),
        )
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.HOME,
                currentRoute = AppRoute.Player("source-1", "episode-1"),
            ),
        )
    }

    @Test
    fun settingsHidesBottomBar() {
        assertFalse(
            appBottomBarVisible(
                selectedTab = AppDestination.PROFILE,
                currentRoute = AppRoute.Settings,
            ),
        )
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
        assertFalse(appBottomBarVisible(AppDestination.SETTINGS, AppRoute.TopLevel(AppTopLevelDestination.PROFILE)))
    }
}
