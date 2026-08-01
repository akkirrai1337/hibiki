package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.WatchSource

class WatchFlowNavigationTest {
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
}
