package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.details.state.resolveDetailsPlaybackAvailability
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

@Composable
internal fun DetailsDestinationContent(
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
    val canWatch = resolveDetailsPlaybackAvailability(
        watchRepositoryAvailable = watchRepositoryAvailable,
        sources = sources,
        selectedSourceId = selectedSourceId,
        status = anime.status,
        episodesLabel = anime.episodesLabel,
    )
    AppDetailsScreen(
        anime = anime,
        onBackClick = onBackFromDetails,
        onRelatedAnimeClick = onRelatedAnimeClick,
        backHandler = { onBack ->
            AppSystemBackHandler(enabled = true, onBack = onBack) {}
        },
        canWatch = canWatch,
        onWatchClick = onWatchClick,
        onTrailerClick = anime.trailer?.playbackUrl?.let { url -> { onOpenUrl(url) } },
        resumeState = detailsResumeState,
        onResumeClick = onResumePlayback,
        resumeFrameContent = detailsResumeState?.let { state ->
            resumeFrameContent?.let { content -> { frameModifier -> content(state.titleId, frameModifier) } }
        },
        libraryRepository = libraryRepository,
        onLibraryCategoryChange = { onLibraryChanged() },
        modifier = modifier,
        isDetailsLoading = isDetailsLoading,
        detailsError = detailsError,
        posterPreviewOpen = detailsPosterPreviewOpen,
        onPosterPreviewOpenChange = onDetailsPosterPreviewOpenChange,
        titleSheetOpen = detailsTitleSheetOpen,
        onTitleSheetOpenChange = onDetailsTitleSheetOpenChange,
        librarySheetOpen = detailsLibrarySheetOpen,
        onLibrarySheetOpenChange = onDetailsLibrarySheetOpenChange,
    )
}
