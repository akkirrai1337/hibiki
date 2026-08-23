package org.akkirrai.hibiki.player

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.akkirrai.hibiki.design.AppMotion
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.AppPlayerErrorOverlay
import org.akkirrai.hibiki.player.AppPlayerLoadingOverlay
import org.akkirrai.hibiki.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.player.EpisodeDownloadState
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.WatchScreenScaffold
import org.akkirrai.hibiki.player.WatchSourcesScreenState
import org.akkirrai.hibiki.player.watchNavigationLockKey
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

internal data class WatchRouteState(
    val anime: Anime,
    val watchState: WatchSourcesScreenState,
    val episodesState: EpisodesScreenState,
    val selectedWatchSource: WatchSource?,
    val profileData: org.akkirrai.hibiki.profile.LocalProfileData,
    val playbackError: String?,
    val playbackLoading: Boolean,
    val playbackHostAvailable: Boolean,
    // No longer drives anything here -- download actions are always visible per-row now, not
    // toggled -- but the deep-link route that sets it still passes it through, see
    // AppDestinationSpecialRoutes.
    @Suppress("UNUSED") val downloadMode: Boolean,
    val isPlayerRoute: Boolean,
)

internal data class WatchRouteActions(
    val onBack: () -> Unit,
    val onWatchRetry: () -> Unit,
    val onWatchLoadMore: () -> Unit,
    val onWatchSourceClick: (WatchSource) -> Unit,
    val onWatchEpisodeClick: (org.akkirrai.hibiki.player.model.WatchEpisode) -> Unit,
    val onLibraryChanged: () -> Unit,
)

@Composable
internal fun HibikiWatchFlowContent(
    state: WatchRouteState,
    actions: WatchRouteActions,
    episodeDownloadRepository: EpisodeDownloadRepository?,
    libraryRepository: LibraryRepository,
    modifier: Modifier = Modifier,
) {
    val anime = state.anime
    val watchState = state.watchState
    val episodesState = state.episodesState
    val selectedWatchSource = state.selectedWatchSource
    val profileData = state.profileData
    val playbackError = state.playbackError
    val playbackLoading = state.playbackLoading
    val playbackHostAvailable = state.playbackHostAvailable
    val isPlayerRoute = state.isPlayerRoute
    val onBack = actions.onBack
    val onWatchRetry = actions.onWatchRetry
    val onWatchLoadMore = actions.onWatchLoadMore
    val onWatchSourceClick = actions.onWatchSourceClick
    val onWatchEpisodeClick = actions.onWatchEpisodeClick
    val onLibraryChanged = actions.onLibraryChanged
    val navigationLockKey = watchNavigationLockKey(
        animeId = anime.id,
        sourceId = selectedWatchSource?.sourceId,
        isPlayerRoute = isPlayerRoute,
    )
    var navigationLocked by remember(navigationLockKey) { mutableStateOf(false) }
    val episodeDownloadSourceId = selectedWatchSource?.sourceId.orEmpty()
    var episodeDownloadStates by remember(episodeDownloadSourceId) {
        mutableStateOf<Map<String, EpisodeDownloadState>>(emptyMap())
    }
    PollEpisodeDownloadStates(
        repository = episodeDownloadRepository,
        sourceId = episodeDownloadSourceId,
        episodes = (episodesState.result as? org.akkirrai.hibiki.player.EpisodesUiState.Content)
            ?.items.orEmpty(),
        onStatesChanged = { episodeDownloadStates = it },
    )
    WatchScreenScaffold(
        onBackClick = {
            if (!navigationLocked) {
                navigationLocked = true
                onBack()
            }
        },
        backEnabled = !navigationLocked,
        backContentDescription = appText(AppTextKey.Back),
        title = selectedWatchSource?.title ?: anime.title,
        modifier = modifier,
    ) { listContentPadding ->
        // The outer root transition now treats sources -> episodes as one continuous slot (so
        // picking a voiceover doesn't cross-fade the *whole* screen, header and all -- see
        // HibikiAppShell's contentRoute/contentTransitionKey for the watch flow). This inner
        // NavHost gives that step its own fade instead, matching every other screen transition's
        // style/duration, just scoped to the content area under the toggle/back bar. It mirrors
        // selectedWatchSource one-way (same pattern as AppProductionRoot's tab NavHost) and its
        // own backstack is pinned to one entry at all times (inclusive = true) so it never
        // registers system-back interception -- WatchScreenScaffold's onBackClick above (wired to
        // the real navigation actions) stays the only way back, same as before this change.
        //
        // NavHost's graph builder (the trailing lambda below) only runs once, memoized on
        // (navController, startDestination) -- so the composable() blocks close over whatever
        // plain values (watchState, episodesState, ...) existed on the FIRST composition, not
        // later ones. rememberUpdatedState keeps live references so episode-list/loading/error
        // updates that arrive after the destination first mounts aren't silently dropped.
        val currentWatchState = rememberUpdatedState(watchState)
        val currentEpisodesState = rememberUpdatedState(episodesState)
        val currentSelectedWatchSource = rememberUpdatedState(selectedWatchSource)
        val currentProfileData = rememberUpdatedState(profileData)
        val currentPlaybackError = rememberUpdatedState(playbackError)
        val currentPlaybackLoading = rememberUpdatedState(playbackLoading)
        val currentPlaybackHostAvailable = rememberUpdatedState(playbackHostAvailable)
        val currentListContentPadding = rememberUpdatedState(listContentPadding)
        val currentOnWatchRetry = rememberUpdatedState(onWatchRetry)
        val currentOnWatchSourceClick = rememberUpdatedState(onWatchSourceClick)
        val currentOnWatchLoadMore = rememberUpdatedState(onWatchLoadMore)
        val currentOnWatchEpisodeClick = rememberUpdatedState(onWatchEpisodeClick)
        val currentOnLibraryChanged = rememberUpdatedState(onLibraryChanged)
        val currentAnime = rememberUpdatedState(anime)
        val currentEpisodeDownloadRepository = rememberUpdatedState(episodeDownloadRepository)
        val currentLibraryRepository = rememberUpdatedState(libraryRepository)
        val watchStepDestination = if (selectedWatchSource == null) "sources" else "episodes"
        val navController = rememberNavController()
        // The very first sources->episodes settle within a freshly-opened watch flow (most
        // visibly the single-voiceover auto-skip, which can resolve while the outer
        // Details->watch-flow entrance fade is still running) doesn't get its own fade -- it
        // would otherwise run concurrently with that outer fade and read as a jerky double
        // animation. withFrameNanos defers flipping the flag to the frame after this
        // navigate() call, so THIS transition still reads the pre-flip (suppressed) value; any
        // later, deliberately re-picked voiceover still gets the normal fade.
        var suppressNextWatchStepTransition by remember { mutableStateOf(true) }
        LaunchedEffect(watchStepDestination) {
            val suppressThisTransition = suppressNextWatchStepTransition
            navController.navigate(watchStepDestination) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
            if (suppressThisTransition) {
                withFrameNanos {}
                suppressNextWatchStepTransition = false
            }
        }
        val currentSuppressTransition = rememberUpdatedState(suppressNextWatchStepTransition)
        NavHost(
            navController = navController,
            startDestination = watchStepDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (currentSuppressTransition.value) EnterTransition.None
                else fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis))
            },
            exitTransition = {
                if (currentSuppressTransition.value) ExitTransition.None
                else fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis))
            },
            popEnterTransition = { fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
            popExitTransition = { fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
        ) {
            composable("sources") {
                WatchSourcesDestinationContent(
                    state = currentWatchState.value,
                    navigationLocked = navigationLocked,
                    onWatchRetry = { currentOnWatchRetry.value() },
                    onWatchSourceClick = { source ->
                        navigationLocked = true
                        currentOnWatchSourceClick.value(source)
                    },
                    onWatchLoadMore = { currentOnWatchLoadMore.value() },
                    listContentPadding = currentListContentPadding.value,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable("episodes") {
                val targetWatchSource = currentSelectedWatchSource.value
                if (targetWatchSource != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HibikiEpisodesContent(
                            state = currentEpisodesState.value,
                            source = targetWatchSource,
                            anime = currentAnime.value,
                            profileData = currentProfileData.value,
                            playbackLoading = currentPlaybackLoading.value,
                            navigationLocked = navigationLocked,
                            // Download actions are always visible per-row now -- see EpisodeRow --
                            // instead of toggled by a top-bar button that used to live here.
                            downloadControlsVisible = true,
                            episodeDownloadRepository = currentEpisodeDownloadRepository.value,
                            episodeDownloadStates = episodeDownloadStates,
                            onEpisodeDownloadStatesChange = { episodeDownloadStates = it },
                            libraryRepository = currentLibraryRepository.value,
                            onEpisodeClick = { episode ->
                                if (!navigationLocked) {
                                    navigationLocked = true
                                    currentOnWatchEpisodeClick.value(episode)
                                }
                            },
                            onLibraryChanged = { currentOnLibraryChanged.value() },
                            onRetry = { currentOnWatchRetry.value() },
                            listContentPadding = currentListContentPadding.value,
                        )
                        if (!currentPlaybackHostAvailable.value) {
                            AppPlayerLoadingOverlay(visible = currentPlaybackLoading.value)
                            currentPlaybackError.value?.let { message ->
                                AppPlayerErrorOverlay(
                                    message = message,
                                    title = appText(AppTextKey.PlayerErrorTitle),
                                    retryLabel = appText(AppTextKey.PlayerRetry),
                                    onRetry = { currentOnWatchRetry.value() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
