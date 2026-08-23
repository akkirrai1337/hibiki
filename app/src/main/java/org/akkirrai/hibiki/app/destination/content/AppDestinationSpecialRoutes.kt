package org.akkirrai.hibiki.app.destination.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.details.screen.DetailsOverlay
import org.akkirrai.hibiki.details.screen.DetailsRoute
import org.akkirrai.hibiki.details.screen.DetailsRouteActions
import org.akkirrai.hibiki.details.screen.DetailsRouteState
import org.akkirrai.hibiki.details.screen.DetailsOverlayState
import org.akkirrai.hibiki.player.HibikiWatchFlowContent
import org.akkirrai.hibiki.player.WatchRouteActions
import org.akkirrai.hibiki.player.WatchRouteState

@Composable
internal fun AppDestinationWatchRoute(input: AppDestinationContentInput) {
    val watch = input.watch
    val platform = input.platform
    val playback = watch.playbackContext
    val content = watch.state
    val profileState by input.profile.profilePresenter.state.collectAsState()
    val watchState by playback.watchPresenter.state.collectAsState()
    val episodesState by playback.episodesPresenter.state.collectAsState()

    HibikiWatchFlowContent(
        state = WatchRouteState(
            anime = requireNotNull(content.watchAnime),
            watchState = watchState,
            episodesState = episodesState,
            selectedWatchSource = content.selectedWatchSource,
            profileData = profileState.data,
            playbackError = content.playbackError,
            playbackLoading = content.playbackLoading,
            playbackHostAvailable = content.playbackHostAvailable,
            downloadMode = playback.downloadMode,
            isPlayerRoute = content.isPlayerRoute,
        ),
        actions = WatchRouteActions(
            onBack = input.navigation.actions.onBackFromWatch,
            onWatchRetry = watch.actions.onRetry,
            onWatchLoadMore = watch.actions.onLoadMore,
            onWatchSourceClick = watch.actions.onSourceClick,
            onWatchEpisodeClick = watch.actions.onEpisodeClick,
            onLibraryChanged = platform.hostContext.onLibraryChanged,
        ),
        episodeDownloadRepository = playback.episodeDownloadRepository,
        libraryRepository = platform.dataContext.libraryRepository,
        modifier = platform.hostContext.modifier,
    )
}

@Composable
internal fun AppDestinationDetailsRoute(
    input: AppDestinationContentInput,
    animeOverride: Anime? = null,
) {
    val content = input.watch.state
    val platform = input.platform
    val playback = input.watch.playbackContext
    val source = input.sources.state
    val anime = animeOverride ?: requireNotNull(content.selectedAnime)
    // Local to this screen, not part of the navigation overlay stack -- DetailsScreen already
    // owns closing this on system back itself (see its own AppSystemBackHandler), so there's
    // nothing left for a shared overlay stack to coordinate.
    var detailsOverlay by remember { mutableStateOf<DetailsOverlay?>(null) }

    DetailsRoute(
        state = DetailsRouteState(
            anime = anime,
            watchRepositoryAvailable = content.watchRepositoryAvailable,
            sources = source.sources,
            selectedSourceId = source.selectedSourceId,
            detailsError = content.detailsError,
            detailsResumeState = content.detailsResumeState,
            overlayState = DetailsOverlayState(
                overlay = detailsOverlay,
                onOverlayChange = { next -> detailsOverlay = next },
            ),
        ),
        actions = DetailsRouteActions(
            onBackFromDetails = input.navigation.actions.onBackFromDetails,
            onRelatedAnimeClick = input.navigation.actions.onAnimeClick,
            onWatchClick = { input.navigation.actions.onWatchClick(anime) },
            onResumePlayback = input.watch.actions.onResumePlayback,
            onLibraryChanged = platform.hostContext.onLibraryChanged,
        ),
        libraryRepository = platform.dataContext.libraryRepository,
        resumeFrameContent = playback.resumeFrameContent,
        modifier = platform.hostContext.modifier.fillMaxSize(),
    )
}
