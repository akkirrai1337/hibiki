package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchFlowBackPolicyTest {
    @Test
    fun `watch flow back returns reduced state and matching effect`() {
        val state = AppNavigationState()
            .navigateToWatchSources("anime")
            .navigateToEpisodes(org.akkirrai.hibiki.player.model.WatchSource("source", "Dub", 12), animeId = "anime")

        val transition = state.reduceWatchFlowBack()

        assertEquals(AppRoute.WatchSources("anime"), transition.state.currentRoute)
        assertEquals(WatchFlowBackEffect.ResetEpisodesAndPlayer, transition.effect)
    }

    @Test
    fun playerBackToEpisodesResetsEpisodesAndPlayerState() {
        assertEquals(
            WatchFlowBackEffect.ResetEpisodesAndPlayer,
            resolveWatchFlowBackEffect(
                routeBeforeBack = AppRoute.Player("source", "episode"),
                routeAfterBack = AppRoute.Episodes(
                    source = org.akkirrai.hibiki.player.model.WatchSource("source", "Dub", 12),
                    animeId = "anime",
                ),
            ),
        )
    }

    @Test
    fun episodesAndSourcesBackResetEpisodesAndPlayerState() {
        val details = AppRoute.Details("anime")

        assertEquals(
            WatchFlowBackEffect.ResetEpisodesAndPlayer,
            resolveWatchFlowBackEffect(AppRoute.Episodes(
                source = org.akkirrai.hibiki.player.model.WatchSource("source", "Dub", 12),
                animeId = "anime",
            ), details),
        )
        assertEquals(
            WatchFlowBackEffect.ResetEpisodesAndPlayer,
            resolveWatchFlowBackEffect(AppRoute.WatchSources("anime"), details),
        )
    }

    @Test
    fun playerBackToDetailsOnlyResetsPlayer() {
        assertEquals(
            WatchFlowBackEffect.ResetPlayer,
            resolveWatchFlowBackEffect(
                AppRoute.Player("source", "episode"),
                AppRoute.Details("anime"),
            ),
        )
    }

    @Test
    fun detailsBackClosesDetailsState() {
        assertEquals(
            WatchFlowBackEffect.CloseDetails,
            resolveWatchFlowBackEffect(
                AppRoute.Details("anime"),
                AppRoute.TopLevel(AppTopLevelDestination.HOME),
            ),
        )
    }
}
