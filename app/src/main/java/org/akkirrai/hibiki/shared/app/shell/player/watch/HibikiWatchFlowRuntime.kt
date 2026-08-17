package org.akkirrai.hibiki.shared.app.shell.player.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.shared.app.shell.player.HibikiPlaybackRequestCoordinator
import org.akkirrai.hibiki.shared.catalog.model.Anime
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
import org.akkirrai.hibiki.shared.player.PlaybackRequest
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.resetForNavigation

internal fun resetHibikiPlayerState(playerPresenter: PlayerPresenter) {
    playerPresenter.setState(playerPresenter.state.value.resetForNavigation())
}

internal fun applyHibikiWatchFlowBackEffect(
    effect: WatchFlowBackEffect,
    invalidateEpisodes: Boolean,
    onEpisodesInvalidated: () -> Unit,
    episodesPresenter: EpisodesPresenter,
    playerPresenter: PlayerPresenter,
) {
    when (effect) {
        WatchFlowBackEffect.ResetEpisodesAndPlayer -> {
            if (invalidateEpisodes) onEpisodesInvalidated()
            episodesPresenter.setState(EpisodesScreenState())
            resetHibikiPlayerState(playerPresenter)
        }
        WatchFlowBackEffect.ResetPlayer,
        WatchFlowBackEffect.CloseDetails,
        WatchFlowBackEffect.None,
        -> resetHibikiPlayerState(playerPresenter)
    }
}

internal class HibikiWatchRetryActions(
    private val lastPlaybackRequest: () -> PlaybackRequest?,
    private val hasPlaybackError: () -> Boolean,
    private val selectedWatchSource: () -> WatchSource?,
    private val retryPlayback: (PlaybackRequest) -> Unit,
    private val retryWatchSources: () -> Unit,
    private val retryEpisodes: () -> Unit,
) {
    val onRetry: () -> Unit = {
        val failedRequest = lastPlaybackRequest()
        if (hasPlaybackError() && failedRequest != null) {
            retryPlayback(failedRequest)
        } else if (selectedWatchSource() == null) {
            retryWatchSources()
        } else {
            retryEpisodes()
        }
    }
}

internal class HibikiWatchFlowState {
    var detailsAnime by mutableStateOf<Anime?>(null)
    var detailsResumeState by mutableStateOf<TitleWatchState?>(null)
    var watchLoadGeneration by mutableStateOf(0)
    var forceWatchSourcesRefresh by mutableStateOf(false)
    var episodesLoadGeneration by mutableStateOf(0)
}

@Composable
internal fun rememberHibikiWatchFlowState(): HibikiWatchFlowState = remember {
    HibikiWatchFlowState()
}

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
        // Reset to loading is left to HibikiWatchDataEffects' LaunchedEffect(watchLoadGeneration),
        // which fires exactly once after navigation completes. Setting it here too made the
        // screen render once with this state, then again with the effect's reset right after
        // the transition finished, showing as a spurious reload.
        incrementWatchLoadGeneration()
        episodesPresenter.setState(EpisodesScreenState())
        cancelPlaybackAndResetPlayer()
        setPlaybackReturnRoute(null)
        setNavigationState(navigationState().navigateToWatchSources(anime.id, downloadMode))
    }

    fun openEpisodes(source: WatchSource, animeId: String?, downloadMode: Boolean) {
        onSourceSelected(animeId.orEmpty(), source)
        cancelPlaybackAndResetPlayer()
        setNavigationState(navigationState().navigateToEpisodes(source, downloadMode, animeId))
    }

    private fun cancelPlaybackAndResetPlayer() {
        playbackSession.cancelAndInvalidate()
        resetPlayerState()
    }

    fun backFromWatch() {
        playbackSession.cancelAndInvalidate()
        val transition = resolveWatchFlowBackTransition(navigationState(), getPlaybackReturnRoute())
        setNavigationState(transition.state)
        if (navigationState().currentRoute !is AppRoute.Player) setPlaybackReturnRoute(null)
        applyBackEffect(transition.effect, false)
    }
}

internal class HibikiAppShellPlaybackEffects(
    private val episodesPresenter: EpisodesPresenter,
    private val playerPresenter: PlayerPresenter,
    private val invalidateEpisodes: () -> Unit,
) {
    fun resetPlayerState() {
        resetHibikiPlayerState(playerPresenter)
    }

    fun applyWatchFlowBackEffect(
        effect: WatchFlowBackEffect,
        invalidateEpisodesState: Boolean = true,
    ) {
        applyHibikiWatchFlowBackEffect(
            effect = effect,
            invalidateEpisodes = invalidateEpisodesState,
            onEpisodesInvalidated = invalidateEpisodes,
            episodesPresenter = episodesPresenter,
            playerPresenter = playerPresenter,
        )
    }
}

internal class HibikiAppShellPlaybackOverlayActions(
    private val playbackSession: HibikiPlaybackSession,
    private val playbackEffects: HibikiAppShellPlaybackEffects,
    private val playbackRequestCoordinator: HibikiPlaybackRequestCoordinator,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val playbackReturnRoute: () -> AppRoute?,
    private val setPlaybackReturnRoute: (AppRoute?) -> Unit,
) {
    fun retry(lastPlaybackRequest: PlaybackRequest?) {
        lastPlaybackRequest?.let { failedRequest ->
            playbackRequestCoordinator.request(
                episode = failedRequest.episode,
                sourceOverride = failedRequest.source,
                preferredPlayerName = failedRequest.preferredPlayerName,
                preferredQuality = failedRequest.preferredQuality,
            )
        }
    }

    fun dismiss() {
        playbackSession.cancelAndInvalidate()
        playbackSession.clearRouteState()
        playbackEffects.resetPlayerState()
        val backTransition = resolveWatchFlowBackTransition(navigationState(), playbackReturnRoute())
        setNavigationState(backTransition.state)
        setPlaybackReturnRoute(null)
    }
}
