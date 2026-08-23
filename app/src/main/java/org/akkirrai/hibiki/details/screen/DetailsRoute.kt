package org.akkirrai.hibiki.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.details.state.resolveDetailsPlaybackAvailability
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.player.model.TitleWatchState
import org.akkirrai.hibiki.platform.AppSystemBackHandler
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

@Composable
internal fun DetailsRoute(
    anime: Anime,
    watchRepositoryAvailable: Boolean,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    libraryRepository: LibraryRepository,
    detailsError: String?,
    detailsResumeState: TitleWatchState?,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)?,
    overlayState: DetailsOverlayState,
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
    DetailsScreen(
        anime = anime,
        actions = DetailsActions(
            onBackClick = onBackFromDetails,
            onRelatedAnimeClick = onRelatedAnimeClick,
            onWatchClick = onWatchClick,
            onTrailerClick = anime.trailer?.playbackUrl?.let { url -> { onOpenUrl(url) } },
            onResumeClick = onResumePlayback,
            onLibraryCategoryChange = { onLibraryChanged() },
        ),
        backHandler = { onBack ->
            AppSystemBackHandler(enabled = true, onBack = onBack) {}
        },
        canWatch = canWatch,
        resumeState = detailsResumeState,
        resumeFrameContent = detailsResumeState?.let { state ->
            resumeFrameContent?.let { content -> { frameModifier -> content(state.titleId, frameModifier) } }
        },
        libraryRepository = libraryRepository,
        modifier = modifier,
        detailsError = detailsError,
        overlayState = overlayState,
    )
}
