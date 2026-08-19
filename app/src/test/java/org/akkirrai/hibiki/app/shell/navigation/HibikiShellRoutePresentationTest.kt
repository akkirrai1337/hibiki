package org.akkirrai.hibiki.app.shell.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.AppTopLevelDestination

class HibikiShellRoutePresentationTest {
    @Test
    fun topLevelRouteMapsToVisibleRootDestination() {
        val presentation = AppNavigationState(
            currentTopLevel = AppTopLevelDestination.CATALOG,
        ).toHibikiShellRoutePresentation()

        assertEquals(AppDestination.CATALOG, presentation.selectedTab)
        assertEquals(AppTopLevelDestination.CATALOG, presentation.topLevelDestination)
        assertFalse(presentation.activeDownloadMode)
        assertFalse(presentation.isPlayerRoute)
    }

    @Test
    fun watchRouteKeepsDownloadModeAndVisibleRootDestination() {
        val presentation = AppNavigationState(
            currentTopLevel = AppTopLevelDestination.HOME,
            backStack = listOf(AppRoute.WatchSources("anime", downloadMode = true)),
        ).toHibikiShellRoutePresentation()

        assertEquals(AppDestination.HOME, presentation.selectedTab)
        assertTrue(presentation.activeDownloadMode)
        assertFalse(presentation.isPlayerRoute)
    }

    @Test
    fun playerRouteIsReportedWithoutDownloadMode() {
        val presentation = AppNavigationState(
            backStack = listOf(
                AppRoute.Episodes(WatchSource("source", "Source", episodeCount = 1)),
                AppRoute.Player("source", "episode"),
            ),
        ).toHibikiShellRoutePresentation()

        assertTrue(presentation.isPlayerRoute)
        assertFalse(presentation.activeDownloadMode)
    }

}
