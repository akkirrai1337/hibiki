package org.akkirrai.hibiki.shared.app.destination.content

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import org.akkirrai.hibiki.shared.app.destination.context.AppDestinationContentInput
import org.akkirrai.hibiki.shared.app.destination.watch.DetailsDestinationRoute
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.HibikiWatchFlowContent

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

    DetailsDestinationRoute(
        anime = anime,
        watchRepositoryAvailable = content.watchRepositoryAvailable,
        sources = source.sources,
        selectedSourceId = source.selectedSourceId,
        libraryRepository = platform.dataContext.libraryRepository,
        isDetailsLoading = content.isDetailsLoading,
        detailsError = content.detailsError,
        detailsResumeState = content.detailsResumeState,
        resumeFrameContent = playback.resumeFrameContent,
        detailsPosterPreviewOpen = overlay.posterPreviewOpen,
        onDetailsPosterPreviewOpenChange = overlay.onPosterPreviewOpenChange,
        detailsTitleSheetOpen = overlay.titleSheetOpen,
        onDetailsTitleSheetOpenChange = overlay.onTitleSheetOpenChange,
        detailsLibrarySheetOpen = overlay.librarySheetOpen,
        onDetailsLibrarySheetOpenChange = overlay.onLibrarySheetOpenChange,
        onBackFromDetails = input.navigation.actions.onBackFromDetails,
        onRelatedAnimeClick = input.navigation.actions.onAnimeClick,
        onWatchClick = { input.navigation.actions.onWatchClick(anime) },
        onOpenUrl = platform.hostContext.onOpenUrl,
        onResumePlayback = input.watch.actions.onResumePlayback,
        onLibraryChanged = platform.hostContext.onLibraryChanged,
        modifier = platform.hostContext.modifier.fillMaxSize(),
    )
}
