package org.akkirrai.hibiki.shared.catalog.screen

import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.shared.design.component.state.AppContentState
import org.akkirrai.hibiki.shared.design.component.state.AppLoadMoreState
import org.akkirrai.hibiki.shared.design.component.content.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.buildCardMeta

/** Common catalog results layer; sorting and filters remain host-owned. */
@Composable
fun AppCatalogResultsContent(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    errorTitle: String,
    retryLabel: String,
    errorIcon: ImageVector,
    announcementLabel: String,
    movieLabel: String,
    posterContent: @Composable androidx.compose.foundation.layout.BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    metaSeparator: String = " • ",
) {
    Box(modifier = modifier.fillMaxSize()) {
        AppContentState(
            isLoading = state.isLoading,
            hasContent = state.items.isNotEmpty(),
            errorMessage = state.error,
            errorTitle = errorTitle,
            retryLabel = retryLabel,
            onRetry = onRetry,
            errorIcon = errorIcon,
            errorIconTint = MaterialTheme.colorScheme.error,
            content = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = topContentPadding,
                        end = UiDimens.ScreenPadding,
                        bottom = bottomContentPadding + UiDimens.ScreenPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    appVerticalAnimeListContent(
                        items = state.items,
                        metaText = { anime ->
                            anime.buildCardMeta(
                                announcementLabel = announcementLabel,
                                movieLabel = movieLabel,
                                maxSubtitleParts = 2,
                                separator = metaSeparator,
                            )
                        },
                        onAnimeClick = onAnimeClick,
                        posterContent = posterContent,
                        posterFooterContent = posterFooterContent,
                        onItemVisible = onItemVisible,
                    )
                    if (state.isLoadingMore || state.error != null) {
                        item(key = "catalog_load_more") {
                            AppLoadMoreState(
                                isLoading = state.isLoadingMore,
                                errorMessage = state.error,
                                errorIcon = errorIcon,
                                onRetry = onLoadMore,
                            )
                        }
                    }
                }
            },
        )

        if (state.isLoading && !state.isLoadingMore && state.items.isNotEmpty()) {
            AppCenteredLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
            )
        }
    }
}
