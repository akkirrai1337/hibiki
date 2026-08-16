package org.akkirrai.hibiki.shared.home.screen

import org.akkirrai.hibiki.shared.home.model.TrendingAnimeUiState
import org.akkirrai.hibiki.shared.home.state.*

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppLoadMoreState
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime

@Composable
fun AppTrendingScreenContent(
    state: TrendingAnimeUiState,
    listState: LazyListState,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    metaText: @Composable (Anime) -> String,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    modifier: Modifier = Modifier,
) {
    AppTrendingContentState(
        isLoading = state.isLoading,
        hasContent = state.items.isNotEmpty(),
        errorMessage = state.errorMessage,
        errorTitle = errorTitle,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
    ) {
        AppTrendingContentList(state = listState) {
            appHomeAnimeListContent(
                items = state.items,
                metaText = metaText,
                onAnimeClick = onAnimeClick,
                libraryStatusByAnimeId = libraryStatusByAnimeId,
                libraryStatusLabel = libraryStatusLabel,
            )
            if (state.isLoadingMore || state.loadMoreError != null) {
                item(key = "trending_load_more_state") {
                    AppLoadMoreState(
                        isLoading = state.isLoadingMore,
                        errorMessage = state.loadMoreError,
                        onRetry = onLoadMoreRetry,
                    )
                }
            }
        }
    }
}
