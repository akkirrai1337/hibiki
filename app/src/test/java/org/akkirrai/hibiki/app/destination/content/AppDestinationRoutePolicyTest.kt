package org.akkirrai.hibiki.app.destination.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.app.destination.watch.AppDestinationContentState
import org.akkirrai.hibiki.app.destination.watch.isDetailsRouteDriven
import org.akkirrai.hibiki.app.destination.watch.isWatchRouteDriven
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.WatchSourcesScreenState
import org.akkirrai.hibiki.player.model.WatchSource

class AppDestinationRoutePolicyTest {
    @Test
    fun explicitWatchRoutesAreWatchDriven() {
        assertTrue(contentState(currentRoute = AppRoute.Player("source", "episode")).isWatchRouteDriven())
        assertTrue(contentState(currentRoute = AppRoute.Episodes(WatchSource("source", "Source", episodeCount = null), animeId = "anime")).isWatchRouteDriven())
        assertFalse(contentState(currentRoute = AppRoute.Details("anime")).isWatchRouteDriven())
    }

    @Test
    fun explicitDetailsRouteIsDetailsDriven() {
        assertTrue(contentState(currentRoute = AppRoute.Details("anime")).isDetailsRouteDriven())
        assertFalse(contentState(currentRoute = AppRoute.Player("source", "episode")).isDetailsRouteDriven())
    }

    @Test
    fun missingRouteFallsBackToAvailableAnimeData() {
        assertTrue(contentState(watchAnime = anime()).isWatchRouteDriven())
        assertTrue(contentState(selectedAnime = anime()).isDetailsRouteDriven())
        assertFalse(contentState().isWatchRouteDriven())
        assertFalse(contentState().isDetailsRouteDriven())
    }

    private fun contentState(
        selectedAnime: Anime? = null,
        watchAnime: Anime? = null,
        currentRoute: AppRoute? = null,
    ) = AppDestinationContentState(
        selectedAnime = selectedAnime,
        watchAnime = watchAnime,
        watchState = WatchSourcesScreenState(),
        episodesState = EpisodesScreenState(),
        selectedWatchSource = null,
        playbackError = null,
        playbackLoading = false,
        watchRepositoryAvailable = false,
        isDetailsLoading = false,
        detailsError = null,
        detailsResumeState = null,
        isPlayerRoute = currentRoute is AppRoute.Player,
        playbackHostAvailable = false,
        currentRoute = currentRoute,
    )

    private fun anime() = Anime(
        id = "anime",
        title = "Anime",
        subtitle = "",
        episodesLabel = "",
        status = "",
    )
}
