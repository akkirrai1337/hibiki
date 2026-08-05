package org.akkirrai.hibiki.shared.app.shell.player.watch

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.navigateToEpisodes
import org.akkirrai.hibiki.shared.navigation.navigateToWatchSources
import org.akkirrai.hibiki.shared.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.HibikiPlaybackSession
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState

internal class HibikiWatchFlowNavigationActions(
    private val watchPresenter: WatchSourcesPresenter,
    private val offlineWatchDataRepository: OfflineWatchDataRepository?,
    private val episodesPresenter: EpisodesPresenter,
    private val playbackSession: HibikiPlaybackSession,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val getPlaybackReturnRoute: () -> AppRoute?,
    private val setPlaybackReturnRoute: (AppRoute?) -> Unit,
    private val setForceWatchSourcesRefresh: (Boolean) -> Unit,
    private val incrementWatchLoadGeneration: () -> Unit,
    private val onSourceSelected: (String, WatchSource) -> Unit,
    private val resetPlayerState: () -> Unit,
    private val applyBackEffect: (WatchFlowBackEffect, Boolean) -> Unit,
) {
    fun openWatch(anime: Anime, downloadMode: Boolean) {
        setForceWatchSourcesRefresh(false)
        incrementWatchLoadGeneration()
        watchPresenter.setState(
            initialWatchSourcesState(
                cachedSources = null,
                offlineSources = offlineWatchDataRepository?.getOfflineSources(anime.id).orEmpty(),
                forceRefresh = true,
            ),
        )
        episodesPresenter.setState(EpisodesScreenState())
        playbackSession.cancelAndInvalidate()
        setPlaybackReturnRoute(null)
        resetPlayerState()
        setNavigationState(navigationState().navigateToWatchSources(anime.id, downloadMode))
    }

    fun openEpisodes(source: WatchSource, animeId: String?, downloadMode: Boolean) {
        onSourceSelected(animeId.orEmpty(), source)
        playbackSession.cancelAndInvalidate()
        resetPlayerState()
        setNavigationState(navigationState().navigateToEpisodes(source, downloadMode, animeId))
    }

    fun backFromWatch() {
        playbackSession.cancelAndInvalidate()
        val transition = resolveWatchFlowBackTransition(navigationState(), getPlaybackReturnRoute())
        setNavigationState(transition.state)
        if (navigationState().currentRoute !is AppRoute.Player) setPlaybackReturnRoute(null)
        applyBackEffect(transition.effect, false)
    }
}
