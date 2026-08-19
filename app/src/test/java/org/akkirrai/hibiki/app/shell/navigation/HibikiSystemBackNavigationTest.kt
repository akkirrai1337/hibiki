package org.akkirrai.hibiki.app.shell.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.app.navigation.currentRoute
import org.akkirrai.hibiki.app.shell.navigation.HibikiBackCleanup
import org.akkirrai.hibiki.app.shell.navigation.reduceHibikiSystemBack

class HibikiSystemBackNavigationTest {
    @Test
    fun overlayIsDismissedBeforeRouteBack() {
        val state = AppNavigationState(
            backStack = listOf(AppRoute.Details("anime")),
            overlays = listOf(AppOverlay.DetailsTitleSheet),
        )

        val result = reduceHibikiSystemBack(state, AppDestination.HOME, false, null)

        assertTrue(result.handled)
        assertEquals(state.backStack, result.state.backStack)
        assertTrue(result.state.overlays.isEmpty())
        assertEquals(HibikiBackCleanup.None, result.cleanup)
    }

    @Test
    fun playerBackReturnsToEpisodesAndRequestsPlayerCleanup() {
        val source = WatchSource("source", "Source", episodeCount = 2)
        val state = AppNavigationState(
            backStack = listOf(
                AppRoute.Episodes(source, animeId = "anime"),
                AppRoute.Player("source", "episode"),
            ),
        )

        val result = reduceHibikiSystemBack(state, AppDestination.HOME, false, null)

        assertTrue(result.handled)
        assertEquals(AppRoute.Episodes(source, animeId = "anime"), result.state.currentRoute)
        assertEquals(HibikiBackCleanup.Player, result.cleanup)
    }

    @Test
    fun detailsBackPreservesCatalogAsTheOriginRoot() {
        val state = AppNavigationState(
            currentTopLevel = AppTopLevelDestination.CATALOG,
            backStack = listOf(AppRoute.Details("anime")),
        )

        val result = reduceHibikiSystemBack(state, AppDestination.CATALOG, false, null)

        assertTrue(result.handled)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.CATALOG), result.state.currentRoute)
        assertEquals(AppTopLevelDestination.CATALOG, result.state.currentTopLevel)
        assertEquals(HibikiBackCleanup.Details, result.cleanup)
    }

    @Test
    fun emptyRootBackIsNotHandled() {
        val result = reduceHibikiSystemBack(AppNavigationState(), AppDestination.HOME, false, null)

        assertFalse(result.handled)
    }

    @Test
    fun emptyNonHomeTabBackReturnsToHomeInsteadOfExitingTheApp() {
        val state = AppNavigationState(currentTopLevel = AppTopLevelDestination.PROFILE)

        val result = reduceHibikiSystemBack(state, AppDestination.PROFILE, false, null)

        assertTrue(result.handled)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.HOME), result.state.currentRoute)
        assertEquals(AppTopLevelDestination.HOME, result.state.currentTopLevel)
    }
}
