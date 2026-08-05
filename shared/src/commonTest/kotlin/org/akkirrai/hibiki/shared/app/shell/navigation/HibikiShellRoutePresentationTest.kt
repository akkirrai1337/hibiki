package org.akkirrai.hibiki.shared.app.shell.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.navigateToExternalSources

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

    @Test
    fun externalSourcesRouteRemainsInsideSettingsDestination() {
        val presentation = AppNavigationState(
            currentTopLevel = AppTopLevelDestination.HOME,
            backStack = listOf(AppRoute.ExternalSources),
        ).toHibikiShellRoutePresentation()

        assertEquals(AppDestination.SETTINGS, presentation.selectedTab)
        assertEquals(AppTopLevelDestination.PROFILE, presentation.topLevelDestination)
        assertEquals(AppRoute.ExternalSources, presentation.currentRoute)
        assertFalse(presentation.isPlayerRoute)
    }

    @Test
    fun reopeningExternalSourcesDoesNotDuplicateRoute() {
        val state = AppNavigationState(
            backStack = listOf(AppRoute.ExternalSources),
        )

        assertEquals(state, state.navigateToExternalSources())
    }
}
