package org.akkirrai.hibiki.shared.home.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.SearchUiState

@Composable
fun AppHomeSearchResultsZone(
    state: SearchUiState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onAnimeClick: (Anime) -> Unit,
    metaText: @Composable (Anime) -> String,
    onLoadMore: () -> Unit,
    onRetrySearch: () -> Unit = {},
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)?,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    onItemVisible: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppHomeFeedList(
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding,
        horizontalPadding = UiDimens.ScreenPadding,
        modifier = modifier,
    ) {
        appSearchResultsContent(
            state = state,
            onAnimeClick = onAnimeClick,
            metaText = metaText,
            onLoadMore = onLoadMore,
            onRetrySearch = onRetrySearch,
            loadMoreLabel = loadMoreLabel,
            resultsCountLabel = resultsCountLabel,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyIcon = emptyIcon,
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            libraryStatusLabel = libraryStatusLabel,
            onItemVisible = onItemVisible,
        )
    }
}
