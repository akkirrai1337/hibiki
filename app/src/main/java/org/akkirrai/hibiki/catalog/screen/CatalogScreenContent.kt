package org.akkirrai.hibiki.catalog.screen

import org.akkirrai.hibiki.catalog.state.*
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.design.component.poster.AppPosterPlaceholder
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.screen.LibraryStatusPosterFooter
import org.akkirrai.hibiki.library.screen.icon
import org.akkirrai.hibiki.catalog.model.Anime

@Composable
fun AppCatalogScreenContent(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    topContentPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    errorTitle: String,
    retryLabel: String,
    announcementLabel: String,
    movieLabel: String,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    onAnimeClick: (Anime) -> Unit,
    onItemVisible: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    isPullRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AppCatalogContentState(
        isLoading = state.isLoading && !isPullRefreshing,
        hasContent = state.items.isNotEmpty(),
        errorMessage = state.error,
        errorTitle = errorTitle,
        retryLabel = retryLabel,
        onRetry = onRetry,
    ) {
        if (state.isLoading && !state.isLoadingMore && state.items.isEmpty() && !isPullRefreshing) {
            AppCenteredLoading(modifier = modifier.fillMaxSize())
        } else {
            AppCatalogContentList(
                state = listState,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
            ) {
                appCatalogResultsContent(
                    items = state.items,
                    announcementLabel = announcementLabel,
                    movieLabel = movieLabel,
                    onAnimeClick = onAnimeClick,
                    posterContent = { anime ->
                        AppPosterImage(
                            primaryUrl = anime.posterUrl,
                            fallbackUrl = anime.posterFallbackUrl,
                            contentDescription = anime.title,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                AppPosterPlaceholder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f),
                                )
                            },
                        )
                    },
                    posterFooterContent = { anime ->
                        libraryStatusByAnimeId[anime.id]?.let { category ->
                            LibraryStatusPosterFooter(
                                label = libraryStatusLabel(category),
                                icon = category.icon(),
                            )
                        }
                    },
                    onItemVisible = onItemVisible,
                    isLoadingMore = state.isLoadingMore,
                    paginationErrorMessage = state.error,
                    onLoadMoreRetry = onLoadMoreRetry,
                )
            }
        }
    }
}

@Composable
private fun AppCatalogContentList(
    state: LazyListState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = topContentPadding,
            end = UiDimens.ScreenPadding,
            bottom = bottomContentPadding + UiDimens.ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(CatalogContentItemGap),
        content = content,
    )
}
