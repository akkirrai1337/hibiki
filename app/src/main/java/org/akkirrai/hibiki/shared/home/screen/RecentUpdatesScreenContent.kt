package org.akkirrai.hibiki.shared.home.screen

import org.akkirrai.hibiki.shared.home.state.*

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppContentState
import org.akkirrai.hibiki.shared.design.component.state.AppLoadMoreState
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime

@Composable
fun AppRecentUpdatesScreenContent(
    isLoading: Boolean,
    items: List<Anime>,
    errorMessage: String?,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    isLoadingMore: Boolean,
    loadMoreError: String?,
    onLoadMoreRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppContentState(
        isLoading = isLoading,
        hasContent = items.isNotEmpty(),
        errorMessage = errorMessage,
        errorTitle = errorTitle,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
    ) {
        AppRecentUpdatesContentList(state = listState) {
            appHomeAnimeListContent(
                items = items,
                onAnimeClick = onAnimeClick,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                libraryStatusLabel = libraryStatusLabel,
                metaText = metaText,
            )
            if (isLoadingMore || loadMoreError != null) {
                item(key = "recent_updates_loading_more") {
                    AppLoadMoreState(
                        isLoading = isLoadingMore,
                        errorMessage = loadMoreError,
                        onRetry = onLoadMoreRetry,
                    )
                }
            }
        }
    }
}
