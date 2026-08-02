package org.akkirrai.hibiki.shared.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.home.appSearchResultsContent
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

@Composable
fun AppSearchScreenContent(
    query: String,
    result: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    placeholder: String,
    searchContentDescription: String,
    searchIcon: ImageVector,
    metaText: @Composable (Anime) -> String,
    onLoadMore: () -> Unit,
    loadMoreLabel: String,
    resultsCountLabel: @Composable (Int) -> String,
    idleTitle: String,
    idleMessage: String,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    retryLabel: String,
    idleIcon: ImageVector = searchIcon,
    idleTopPadding: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    AppSearchContentList(modifier = modifier) {
        item {
            AppSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                placeholder = placeholder,
                searchContentDescription = searchContentDescription,
                searchIcon = searchIcon,
            )
        }
        appSearchResultsContent(
            state = result,
            onAnimeClick = onAnimeClick,
            metaText = metaText,
            onLoadMore = onLoadMore,
            onRetrySearch = onSearch,
            loadMoreLabel = loadMoreLabel,
            resultsCountLabel = resultsCountLabel,
            idleTitle = idleTitle,
            idleMessage = idleMessage,
            idleIcon = idleIcon,
            idleTopPadding = idleTopPadding,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyIcon = emptyIcon,
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            libraryStatusLabel = libraryStatusLabel,
            errorModifier = Modifier.padding(top = 24.dp),
            errorRetryLabel = retryLabel,
            loadMoreModifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
    }
}
