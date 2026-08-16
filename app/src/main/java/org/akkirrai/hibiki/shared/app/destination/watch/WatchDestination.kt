package org.akkirrai.hibiki.shared.app.destination.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.details.screen.DetailsDestinationContent
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.EpisodesScreenState
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

internal data class AppDestinationWatchActions(
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onSourceClick: (WatchSource) -> Unit,
    val onEpisodeClick: (WatchEpisode) -> Unit,
    val onResumePlayback: (TitleWatchState) -> Unit,
)

internal data class AppDestinationContentState(
    val selectedAnime: Anime?,
    val watchAnime: Anime?,
    val watchState: WatchSourcesScreenState,
    val episodesState: EpisodesScreenState,
    val selectedWatchSource: WatchSource?,
    val playbackError: String?,
    val playbackLoading: Boolean,
    val watchRepositoryAvailable: Boolean,
    val isDetailsLoading: Boolean,
    val detailsError: String?,
    val detailsResumeState: TitleWatchState?,
    val isPlayerRoute: Boolean,
    val playbackHostAvailable: Boolean,
    val currentRoute: AppRoute?,
)

internal data class AppDestinationDetailsOverlayState(
    val posterPreviewOpen: Boolean?,
    val onPosterPreviewOpenChange: ((Boolean) -> Unit)?,
    val titleSheetOpen: Boolean?,
    val onTitleSheetOpenChange: ((Boolean) -> Unit)?,
    val librarySheetOpen: Boolean?,
    val onLibrarySheetOpenChange: ((Boolean) -> Unit)?,
)

internal data class AppDestinationPlaybackContext(
    val episodeDownloadRepository: EpisodeDownloadRepository?,
    val offlineWatchDataRepository: OfflineWatchDataRepository?,
    val offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    val resumeFrameContent: (@Composable (String, Modifier) -> Unit)?,
    val downloadMode: Boolean,
)

internal fun AppDestinationContentState.isWatchRouteDriven(): Boolean =
    currentRoute?.let { route ->
        route is AppRoute.WatchSources || route is AppRoute.Episodes || route is AppRoute.Player
    } ?: (watchAnime != null)

internal fun AppDestinationContentState.isDetailsRouteDriven(): Boolean =
    currentRoute?.let { it is AppRoute.Details } ?: (selectedAnime != null)

@Composable
internal fun DetailsDestinationRoute(
    anime: Anime,
    watchRepositoryAvailable: Boolean,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    libraryRepository: LibraryRepository,
    isDetailsLoading: Boolean,
    detailsError: String?,
    detailsResumeState: TitleWatchState?,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)?,
    detailsPosterPreviewOpen: Boolean?,
    onDetailsPosterPreviewOpenChange: ((Boolean) -> Unit)?,
    detailsTitleSheetOpen: Boolean?,
    onDetailsTitleSheetOpenChange: ((Boolean) -> Unit)?,
    detailsLibrarySheetOpen: Boolean?,
    onDetailsLibrarySheetOpenChange: ((Boolean) -> Unit)?,
    onBackFromDetails: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onWatchClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onResumePlayback: (TitleWatchState) -> Unit,
    onLibraryChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailsDestinationContent(
        anime = anime,
        watchRepositoryAvailable = watchRepositoryAvailable,
        sources = sources,
        selectedSourceId = selectedSourceId,
        libraryRepository = libraryRepository,
        isDetailsLoading = isDetailsLoading,
        detailsError = detailsError,
        detailsResumeState = detailsResumeState,
        resumeFrameContent = resumeFrameContent,
        detailsPosterPreviewOpen = detailsPosterPreviewOpen,
        onDetailsPosterPreviewOpenChange = onDetailsPosterPreviewOpenChange,
        detailsTitleSheetOpen = detailsTitleSheetOpen,
        onDetailsTitleSheetOpenChange = onDetailsTitleSheetOpenChange,
        detailsLibrarySheetOpen = detailsLibrarySheetOpen,
        onDetailsLibrarySheetOpenChange = onDetailsLibrarySheetOpenChange,
        onBackFromDetails = onBackFromDetails,
        onRelatedAnimeClick = onRelatedAnimeClick,
        onWatchClick = onWatchClick,
        onOpenUrl = onOpenUrl,
        onResumePlayback = onResumePlayback,
        onLibraryChanged = onLibraryChanged,
        modifier = modifier,
    )
}
