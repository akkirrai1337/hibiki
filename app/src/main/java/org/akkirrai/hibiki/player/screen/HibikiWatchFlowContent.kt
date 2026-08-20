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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.design.AppMotion
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.AppEpisodesDownloadToggle
import org.akkirrai.hibiki.player.AppPlayerErrorOverlay
import org.akkirrai.hibiki.player.AppPlayerLoadingOverlay
import org.akkirrai.hibiki.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.player.EpisodeDownloadState
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.WatchScreenScaffold
import org.akkirrai.hibiki.player.WatchSourcesScreenState
import org.akkirrai.hibiki.player.watchNavigationLockKey
import org.akkirrai.hibiki.player.rememberEpisodesDownloadControlsVisible
import org.akkirrai.hibiki.player.EpisodesDownloadToggleEndPadding
import org.akkirrai.hibiki.player.EpisodesDownloadToggleTopPadding
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText
import org.akkirrai.hibiki.layout.appTopSystemInsetPadding

@Composable
internal fun HibikiWatchFlowContent(
    anime: Anime,
    watchState: WatchSourcesScreenState,
    episodesState: EpisodesScreenState,
    selectedWatchSource: WatchSource?,
    profileData: org.akkirrai.hibiki.profile.LocalProfileData,
    playbackError: String?,
    playbackLoading: Boolean,
    playbackHostAvailable: Boolean,
    downloadMode: Boolean,
    isPlayerRoute: Boolean,
    episodeDownloadRepository: EpisodeDownloadRepository?,
    libraryRepository: LibraryRepository,
    onBack: () -> Unit,
    onWatchRetry: () -> Unit,
    onWatchLoadMore: () -> Unit,
    onWatchSourceClick: (WatchSource) -> Unit,
    onWatchEpisodeClick: (org.akkirrai.hibiki.player.model.WatchEpisode) -> Unit,
    onLibraryChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationLockKey = watchNavigationLockKey(
        animeId = anime.id,
        sourceId = selectedWatchSource?.sourceId,
        isPlayerRoute = isPlayerRoute,
    )
    var navigationLocked by remember(navigationLockKey) { mutableStateOf(false) }
    val topInsetModifier = Modifier.appTopSystemInsetPadding()
    val episodeDownloadSourceId = selectedWatchSource?.sourceId.orEmpty()
    val downloadControlsVisible = rememberEpisodesDownloadControlsVisible(
        sourceId = episodeDownloadSourceId,
        downloadMode = downloadMode,
    )
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
                    // HibikiEpisodesContent wraps a full-size scrollable episode list, so it must
                    // be declared (and thus z-ordered) BEFORE the toggle button below -- a Box
                    // hit-tests overlapping children in declaration order, and a scrollable's
                    // pointer input spans its whole layout bounds even where it paints nothing, so
                    // a button declared earlier (= visually underneath for input purposes) never
                    // receives its taps.
                    HibikiEpisodesContent(
                        state = episodesState,
                        source = targetWatchSource,
                        anime = anime,
                        profileData = profileData,
                        playbackLoading = playbackLoading,
                        navigationLocked = navigationLocked,
                        downloadControlsVisible = downloadControlsVisible.value,
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
                    if (episodeDownloadRepository != null) {
                        AppEpisodesDownloadToggle(
                            isVisible = downloadControlsVisible.value,
                            contentDescription = appText(AppTextKey.WatchDownload),
                            onClick = { downloadControlsVisible.value = !downloadControlsVisible.value },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .then(topInsetModifier)
                                .padding(
                                    end = EpisodesDownloadToggleEndPadding,
                                    top = EpisodesDownloadToggleTopPadding,
                                ),
                        )
                    }
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
