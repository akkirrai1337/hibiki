package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.WatchSource

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

    @Test
    fun navigationStateOverloadUsesTheCommonRouteAndOverlayPolicy() {
        val details = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
        val overlay = details.reduce(
            AppNavigationEvent.PresentOverlay(AppOverlay.Sheet("details")),
        )

        assertTrue(appBackHandlerEnabled(details))
        assertTrue(appBackHandlerEnabled(overlay))
        assertFalse(appBackHandlerEnabled(AppNavigationState()))
    }

    @Test
    fun fullWatchFlowKeepsBackBridgeEnabledUntilDetailsIsReached() {
        val source = WatchSource("source-1", "Dub", 12)
        val details = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
        val player = details
            .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Episodes(source, animeId = "anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))

        assertTrue(appBackHandlerEnabled(player))
        val playerWithOverlay = player.reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
        assertFalse(appBackHandlerEnabled(playerWithOverlay))
        val episodes = playerWithOverlay.reduce(AppNavigationEvent.Back).reduce(AppNavigationEvent.Back)
        assertTrue(appBackHandlerEnabled(episodes))
        val sources = episodes.reduce(AppNavigationEvent.Back)
        assertTrue(appBackHandlerEnabled(sources))
        assertTrue(appBackHandlerEnabled(sources.reduce(AppNavigationEvent.Back)))
    }

    @Test
    fun playerOverlayUsesItsOwnBackHandler() {
        val player = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))

        assertFalse(appBackHandlerEnabled(player))
        assertTrue(appBackHandlerEnabled(player.reduce(AppNavigationEvent.DismissOverlay)))
    }
}
