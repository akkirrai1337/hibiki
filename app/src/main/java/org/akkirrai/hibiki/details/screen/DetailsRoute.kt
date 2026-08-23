package org.akkirrai.hibiki.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.akkirrai.hibiki.details.state.resolveDetailsPlaybackAvailability
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.player.model.TitleWatchState
import org.akkirrai.hibiki.platform.AppSystemBackHandler
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

internal data class DetailsRouteState(
    val anime: Anime,
    val watchRepositoryAvailable: Boolean,
    val sources: List<AppSourceDescriptor>,
    val selectedSourceId: String?,
    val detailsError: String?,
    val detailsResumeState: TitleWatchState?,
    val overlayState: DetailsOverlayState,
)

internal data class DetailsRouteActions(
    val onBackFromDetails: () -> Unit,
    val onRelatedAnimeClick: (Anime) -> Unit,
    val onWatchClick: () -> Unit,
    val onResumePlayback: (TitleWatchState) -> Unit,
    val onLibraryChanged: () -> Unit,
)

@Composable
internal fun DetailsRoute(
    state: DetailsRouteState,
    actions: DetailsRouteActions,
    libraryRepository: LibraryRepository,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val anime = state.anime
    val uriHandler = LocalUriHandler.current
    val canWatch = resolveDetailsPlaybackAvailability(
        watchRepositoryAvailable = state.watchRepositoryAvailable,
        sources = state.sources,
        selectedSourceId = state.selectedSourceId,
        status = anime.status,
        episodesLabel = anime.episodesLabel,
    )
    var libraryCategory: LibraryCategory? by remember(anime.id) {
        mutableStateOf(libraryRepository.getLibraryCategory(anime.id))
    }
    DetailsScreen(
        anime = anime,
        actions = DetailsActions(
            onBackClick = actions.onBackFromDetails,
            onRelatedAnimeClick = actions.onRelatedAnimeClick,
            onWatchClick = actions.onWatchClick,
            onTrailerClick = anime.trailer?.playbackUrl?.let { url -> { uriHandler.openUri(url) } },
            onResumeClick = actions.onResumePlayback,
            onLibraryCategorySelected = { category ->
                if (category != null) {
                    libraryRepository.saveToLibrary(anime, category)
                } else {
                    libraryRepository.removeFromLibrary(anime.id)
                }
                libraryCategory = category
                actions.onLibraryChanged()
            },
        ),
        backHandler = { onBack ->
            AppSystemBackHandler(enabled = true, onBack = onBack) {}
        },
        canWatch = canWatch,
        resumeState = state.detailsResumeState,
        resumeFrameContent = state.detailsResumeState?.let { resumeState ->
            resumeFrameContent?.let { content -> { frameModifier -> content(resumeState.titleId, frameModifier) } }
        },
        libraryCategory = libraryCategory,
        modifier = modifier,
        detailsError = state.detailsError,
        overlayState = state.overlayState,
    )
}
