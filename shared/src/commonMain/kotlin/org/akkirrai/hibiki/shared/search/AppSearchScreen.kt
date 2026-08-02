package org.akkirrai.hibiki.shared.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.appSearchStateVerticalListContent
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState
import org.akkirrai.hibiki.shared.model.buildCardMeta

/**
 * Shared render layer for the production search screen.
 *
 * The host owns the ViewModel and platform image/footer slots. Everything that
 * determines the search screen hierarchy and state rendering is common.
 */
@Composable
fun AppSearchScreen(
    state: SearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onLoadMore: () -> Unit,
    onRetrySearch: () -> Unit,
    placeholder: String,
    searchContentDescription: String?,
    searchIcon: ImageVector,
    announcementLabel: String,
    movieLabel: String,
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)? = null,
    idleTitle: String,
    idleMessage: String,
    idleIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    retryLabel: String,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = UiDimens.ScreenPadding,
            end = UiDimens.ScreenPadding,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        appSearchStateVerticalListContent(
            state = state,
            onAnimeClick = onAnimeClick,
            metaText = { anime ->
                anime.buildCardMeta(
                    announcementLabel = announcementLabel,
                    movieLabel = movieLabel,
                )
            },
            onLoadMore = onLoadMore,
            onRetrySearch = onRetrySearch,
            loadMoreLabel = loadMoreLabel,
            resultsCountLabel = resultsCountLabel,
            idleTitle = idleTitle,
            idleMessage = idleMessage,
            idleIcon = idleIcon,
            idleTopPadding = 64.dp,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyIcon = emptyIcon,
            errorModifier = Modifier.padding(top = 24.dp),
            errorRetryLabel = retryLabel,
            loadMoreModifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            posterContent = posterContent,
            posterFooterContent = posterFooterContent,
        )
    }
}
