package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.player.model.WatchSource

class WatchFlowNavigationTest {
    @Test
    fun `settings navigation is idempotent when settings is already visible`() {
        val state = AppNavigationState().navigateToSettings()

        assertEquals(AppRoute.Settings, state.currentRoute)
        assertEquals(state, state.navigateToSettings())
    }

    @Test
    fun `details navigation creates a forward route entry`() {
        val state = AppNavigationState().navigateToDetails("anime-1")

        assertEquals(AppRoute.Details("anime-1"), state.currentRoute)
        assertEquals(listOf(AppRoute.Details("anime-1")), state.backStack)
        assertEquals(AppTransitionDirection.Forward, state.transitionDirection)
    }

    @Test
    fun `details back is a no-op outside details route`() {
        val state = AppNavigationState()
            .navigateToDetails("anime-1")
            .navigateBackFromDetails()

        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.HOME), state.currentRoute)
        assertEquals(state, state.navigateBackFromDetails())
    }

    @Test
    fun `details to sources keeps anime and download mode`() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .navigateToWatchSources("anime-1", downloadMode = true)

        assertEquals(
            AppRoute.WatchSources("anime-1", downloadMode = true),
            state.currentRoute,
        )
        assertEquals(AppTransitionDirection.Forward, state.transitionDirection)
        assertEquals(
            AppRoute.WatchSources("anime-1", downloadMode = true).transitionKey(),
            state.currentTransitionKey,
        )
    }

    @Test
    fun `sources to episodes keeps source anime and download mode`() {
        val source = WatchSource("source-1", "Dub", 12)

        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.WatchSources("anime-1")))
            .navigateToEpisodes(source, downloadMode = true, animeId = "anime-1")

        assertEquals(
            AppRoute.Episodes(source, downloadMode = true, animeId = "anime-1"),
            state.currentRoute,
        )
        assertEquals(AppTransitionDirection.Forward, state.transitionDirection)
        assertEquals(
            AppRoute.Episodes(source, downloadMode = true, animeId = "anime-1").transitionKey(),
            state.currentTransitionKey,
        )
    }

    @Test
    fun `episodes to player navigates and player to player replaces`() {
        val first = AppNavigationState()
            .navigateToEpisodes(
                source = WatchSource("source-1", "Dub", 12),
                animeId = "anime-1",
            )
            .navigateToPlayer("source-1", "episode-1", 1.0)

        assertEquals(AppTransitionDirection.Forward, first.transitionDirection)
        assertEquals(
            AppRoute.Player("source-1", "episode-1", 1.0),
            first.currentRoute,
        )

        val replaced = first.navigateToPlayer("source-1", "episode-2", 2.0)

        assertEquals(
            listOf(
                AppRoute.Episodes(WatchSource("source-1", "Dub", 12), animeId = "anime-1"),
                AppRoute.Player("source-1", "episode-2", 2.0),
            ),
            replaced.backStack,
        )
        assertEquals(AppTransitionDirection.Forward, replaced.transitionDirection)
    }
}
