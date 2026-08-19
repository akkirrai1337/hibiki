package org.akkirrai.hibiki.player

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
        if (selectedWatchSource == null) {
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
            val currentWatchSource = requireNotNull(selectedWatchSource)
            Box(modifier = Modifier.fillMaxSize()) {
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
                HibikiEpisodesContent(
                    state = episodesState,
                    source = currentWatchSource,
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
