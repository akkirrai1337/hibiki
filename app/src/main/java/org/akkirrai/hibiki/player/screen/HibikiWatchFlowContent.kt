package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        // AnimatedContent gives that step its own fade instead, matching every other screen
        // transition's style/duration, just scoped to the content area under the toggle/back bar.
        AnimatedContent(
            targetState = selectedWatchSource,
            transitionSpec = {
                fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) togetherWith
                    fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis))
            },
            label = "watch_flow_step_transition",
            modifier = Modifier.fillMaxSize(),
        ) { targetWatchSource ->
            if (targetWatchSource == null) {
                WatchSourcesDestinationContent(
                    state = watchState,
                    navigationLocked = navigationLocked,
                    onWatchRetry = onWatchRetry,
                    onWatchSourceClick = { source ->
                        navigationLocked = true
                        onWatchSourceClick(source)
                    },
                    onWatchLoadMore = onWatchLoadMore,
                    listContentPadding = listContentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    HibikiEpisodesContent(
                        state = episodesState,
                        source = targetWatchSource,
                        anime = anime,
                        profileData = profileData,
                        playbackLoading = playbackLoading,
                        navigationLocked = navigationLocked,
                        // Download actions are always visible per-row now -- see EpisodeRow --
                        // instead of toggled by a top-bar button that used to live here.
                        downloadControlsVisible = true,
                        episodeDownloadRepository = episodeDownloadRepository,
                        episodeDownloadStates = episodeDownloadStates,
                        onEpisodeDownloadStatesChange = { episodeDownloadStates = it },
                        libraryRepository = libraryRepository,
                        onEpisodeClick = { episode ->
                            if (!navigationLocked) {
                                navigationLocked = true
                                onWatchEpisodeClick(episode)
                            }
                        },
                        onLibraryChanged = onLibraryChanged,
                        onRetry = onWatchRetry,
                        listContentPadding = listContentPadding,
                    )
                    if (!playbackHostAvailable) {
                        AppPlayerLoadingOverlay(visible = playbackLoading)
                        playbackError?.let { message ->
                            AppPlayerErrorOverlay(
                                message = message,
                                title = appText(AppTextKey.PlayerErrorTitle),
                                retryLabel = appText(AppTextKey.PlayerRetry),
                                onRetry = onWatchRetry,
                            )
                        }
                    }
                }
            }
        }
    }
}
