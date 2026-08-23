package org.akkirrai.hibiki.app.destination.content

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import org.akkirrai.hibiki.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.details.screen.DetailsOverlay
import org.akkirrai.hibiki.details.screen.DetailsRoute
import org.akkirrai.hibiki.details.screen.DetailsOverlayState
import org.akkirrai.hibiki.player.HibikiWatchFlowContent

@Composable
internal fun AppDestinationWatchRoute(input: AppDestinationContentInput) {
    val watch = input.watch
    val platform = input.platform
    val playback = watch.playbackContext
    val content = watch.state

    HibikiWatchFlowContent(
        anime = requireNotNull(content.watchAnime),
        watchState = content.watchState,
        episodesState = content.episodesState,
        selectedWatchSource = content.selectedWatchSource,
        profileData = input.profile.state.data,
        playbackError = content.playbackError,
        playbackLoading = content.playbackLoading,
        playbackHostAvailable = content.playbackHostAvailable,
        downloadMode = playback.downloadMode,
        isPlayerRoute = content.isPlayerRoute,
        episodeDownloadRepository = playback.episodeDownloadRepository,
        libraryRepository = platform.dataContext.libraryRepository,
        onBack = input.navigation.actions.onBackFromWatch,
        onWatchRetry = watch.actions.onRetry,
        onWatchLoadMore = watch.actions.onLoadMore,
        onWatchSourceClick = watch.actions.onSourceClick,
        onWatchEpisodeClick = watch.actions.onEpisodeClick,
        onLibraryChanged = platform.hostContext.onLibraryChanged,
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
    val overlay = input.navigation.detailsOverlayState
    val anime = animeOverride ?: requireNotNull(content.selectedAnime)

    DetailsRoute(
        anime = anime,
        watchRepositoryAvailable = content.watchRepositoryAvailable,
        sources = source.sources,
        selectedSourceId = source.selectedSourceId,
        libraryRepository = platform.dataContext.libraryRepository,
        detailsError = content.detailsError,
        detailsResumeState = content.detailsResumeState,
        resumeFrameContent = playback.resumeFrameContent,
        overlayState = DetailsOverlayState(
            overlay = when {
                overlay.posterPreviewOpen == true -> DetailsOverlay.Poster
                overlay.titleSheetOpen == true -> DetailsOverlay.Title
                overlay.librarySheetOpen == true -> DetailsOverlay.Library
                else -> null
            },
            onOverlayChange = { next ->
                // Close whichever overlay is currently active before opening the next one --
                // the shell tracks these as a navigation overlay stack, so closing after opening
                // the replacement would try to pop the wrong (new) entry instead.
                if (next != DetailsOverlay.Poster) overlay.onPosterPreviewOpenChange?.invoke(false)
                if (next != DetailsOverlay.Title) overlay.onTitleSheetOpenChange?.invoke(false)
                if (next != DetailsOverlay.Library) overlay.onLibrarySheetOpenChange?.invoke(false)
                when (next) {
                    DetailsOverlay.Poster -> overlay.onPosterPreviewOpenChange?.invoke(true)
                    DetailsOverlay.Title -> overlay.onTitleSheetOpenChange?.invoke(true)
                    DetailsOverlay.Library -> overlay.onLibrarySheetOpenChange?.invoke(true)
                    null -> Unit
                }
            },
        ),
        onBackFromDetails = input.navigation.actions.onBackFromDetails,
        onRelatedAnimeClick = input.navigation.actions.onAnimeClick,
        onWatchClick = { input.navigation.actions.onWatchClick(anime) },
        onOpenUrl = platform.hostContext.onOpenUrl,
        onResumePlayback = input.watch.actions.onResumePlayback,
        onLibraryChanged = platform.hostContext.onLibraryChanged,
        modifier = platform.hostContext.modifier.fillMaxSize(),
    )
}
