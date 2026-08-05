package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.reduceHibikiRootTabSelection
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.HibikiPlaybackSession

internal class HibikiRootNavigationCoordinator(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val selectedTab: () -> AppDestination,
    private val presenter: AnimeCatalogPresenter,
    private val setDetailsAnime: (org.akkirrai.hibiki.shared.catalog.model.Anime?) -> Unit,
    private val playbackSession: HibikiPlaybackSession,
    private val episodesPresenter: EpisodesPresenter,
    private val resetPlayerState: () -> Unit,
) {
    fun select(destination: AppDestination) {
        val result = reduceHibikiRootTabSelection(
            state = navigationState(),
            selectedTab = selectedTab(),
            destination = destination,
        )
        if (!result.handled) return
        setNavigationState(result.state)
        presenter.clearDetails()
        setDetailsAnime(null)
        playbackSession.cancelAndInvalidate()
        playbackSession.clearRouteState()
        episodesPresenter.setState(EpisodesScreenState())
        resetPlayerState()
    }
}

internal fun createHibikiRootNavigationCoordinator(
    navigationState: () -> AppNavigationState,
    setNavigationState: (AppNavigationState) -> Unit,
    selectedTab: () -> AppDestination,
    presenter: AnimeCatalogPresenter,
    setDetailsAnime: (org.akkirrai.hibiki.shared.catalog.model.Anime?) -> Unit,
    playbackSession: HibikiPlaybackSession,
    episodesPresenter: EpisodesPresenter,
    resetPlayerState: () -> Unit,
): HibikiRootNavigationCoordinator = HibikiRootNavigationCoordinator(
    navigationState = navigationState,
    setNavigationState = setNavigationState,
    selectedTab = selectedTab,
    presenter = presenter,
    setDetailsAnime = setDetailsAnime,
    playbackSession = playbackSession,
    episodesPresenter = episodesPresenter,
    resetPlayerState = resetPlayerState,
)
