package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.details.screen.DetailsDestinationContent
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

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
