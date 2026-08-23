package org.akkirrai.hibiki.app.shell.player.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.akkirrai.hibiki.app.shell.player.HibikiPlaybackRequestCoordinator
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.app.navigation.currentRoute
import org.akkirrai.hibiki.app.navigation.navigateToEpisodes
import org.akkirrai.hibiki.app.navigation.navigateToWatchSources
import org.akkirrai.hibiki.app.navigation.replaceWatchSourcesWithEpisodes
import org.akkirrai.hibiki.app.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.player.EpisodesPresenter
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.HibikiPlaybackSession
import org.akkirrai.hibiki.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.player.PlaybackRequest
import org.akkirrai.hibiki.player.PlayerPresenter
import org.akkirrai.hibiki.player.WatchSourcesPresenter
import org.akkirrai.hibiki.player.initialWatchSourcesState
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.resetForNavigation

internal fun resetHibikiPlayerState(playerPresenter: PlayerPresenter) {
    playerPresenter.setState(playerPresenter.state.value.resetForNavigation())
}

internal fun applyHibikiWatchFlowBackEffect(
    effect: WatchFlowBackEffect,
    invalidateEpisodes: Boolean,
    onEpisodesInvalidated: () -> Unit,
    playerPresenter: PlayerPresenter,
) {
    when (effect) {
        WatchFlowBackEffect.ResetEpisodesAndPlayer -> {
            // Not blanking episodesPresenter here: HibikiWatchDataEffects' episodes LaunchedEffect
            // already resets to Loading itself when the next selected source actually differs from
            // the last one it loaded, and skips that reset when it's the same source. Blanking
            // eagerly here too raced with the exit animation of this screen -- the fading-out
            // Episodes slot reads presenter state live, so it would flash to a loading spinner
            // over itself the instant Back was pressed, before the fade even started.
            if (invalidateEpisodes) onEpisodesInvalidated()
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

    /**
     * Called once sources finish loading with exactly one voiceover -- skips the single-item
     * sources list and lands directly on its episodes. Guarded on still being on WatchSources so
     * a late callback (e.g. after the user already backed out) can't hijack navigation elsewhere.
     */
    fun autoSelectSingleWatchSource(source: WatchSource, animeId: String?, downloadMode: Boolean) {
        if (navigationState().currentRoute !is AppRoute.WatchSources) return
        onSourceSelected(animeId.orEmpty(), source)
        cancelPlaybackAndResetPlayer()
        setNavigationState(navigationState().replaceWatchSourcesWithEpisodes(source, downloadMode, animeId))
    }

    private fun cancelPlaybackAndResetPlayer() {
        playbackSession.cancelAndInvalidate()
        resetPlayerState()
    }

    fun backFromWatch() {
        // teardown(), not cancelAndInvalidate() -- this is the in-screen "back" action leaving
        // an active/pending playback screen, the same case HibikiPlaybackSession.teardown()'s own
        // doc comment describes. cancelAndInvalidate() alone skips onExitPlayback, so backing out
        // of the player via this button (as opposed to the system back gesture, which already
        // goes through teardown()) never saved the resume frame/progress -- Details would then
        // show the poster instead of the last-played frame.
        playbackSession.teardown()
        val transition = resolveWatchFlowBackTransition(navigationState(), getPlaybackReturnRoute())
        setNavigationState(transition.state)
        if (navigationState().currentRoute !is AppRoute.Player) setPlaybackReturnRoute(null)
        applyBackEffect(transition.effect, false)
    }
}

internal class HibikiAppShellPlaybackEffects(
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
        playbackSession.teardown()
        playbackEffects.resetPlayerState()
        val backTransition = resolveWatchFlowBackTransition(navigationState(), playbackReturnRoute())
        setNavigationState(backTransition.state)
        setPlaybackReturnRoute(null)
    }
}
