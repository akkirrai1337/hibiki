package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.app.navigation.currentRoute

class HibikiWatchFlowTransitionTest {
    @Test
    fun playerBackRestoresEpisodesReturnRoute() {
        val episodes = AppRoute.Episodes(
            source = WatchSource("source", "Dub", 12),
            animeId = "anime",
        )
        val state = AppNavigationState(
            backStack = listOf(AppRoute.Details("anime"), AppRoute.Player("source", "episode")),
        )

        val transition = resolveWatchFlowBackTransition(state, episodes)

        assertEquals(episodes, transition.state.currentRoute)
        assertEquals(WatchFlowBackEffect.ResetEpisodesAndPlayer, transition.effect)
    }

    @Test
    fun backTransitionUsesNavigationStateWhenNoReturnRouteIsPending() {
        val state = AppNavigationState(
            backStack = listOf(AppRoute.Details("anime"), AppRoute.Player("source", "episode")),
        )

        val transition = resolveWatchFlowBackTransition(state, null)

        assertEquals(AppRoute.Details("anime"), transition.state.currentRoute)
        assertEquals(WatchFlowBackEffect.ResetPlayer, transition.effect)
    }
}
