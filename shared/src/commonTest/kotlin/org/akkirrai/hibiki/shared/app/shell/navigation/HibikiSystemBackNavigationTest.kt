package org.akkirrai.hibiki.shared.app.shell.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.app.shell.navigation.HibikiBackCleanup
import org.akkirrai.hibiki.shared.app.shell.navigation.reduceHibikiSystemBack

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
    fun settingsBackPopsNestedSettingsRoute() {
        val state = AppNavigationState(backStack = listOf(AppRoute.Settings, AppRoute.ExternalSources))

        val result = reduceHibikiSystemBack(state, AppDestination.SETTINGS, false, null)

        assertTrue(result.handled)
        assertEquals(AppRoute.Settings, result.state.currentRoute)
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
    fun emptyRootBackIsNotHandled() {
        val result = reduceHibikiSystemBack(AppNavigationState(), AppDestination.HOME, false, null)

        assertFalse(result.handled)
    }
}
