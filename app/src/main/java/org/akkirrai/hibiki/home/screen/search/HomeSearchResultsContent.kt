package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.design.component.poster.AppPosterPlaceholder
import org.akkirrai.hibiki.design.component.search.appSearchStateVerticalListContent
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.screen.LibraryStatusPosterFooter
import org.akkirrai.hibiki.library.screen.icon
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.SearchUiState

fun LazyListScope.appSearchResultsContent(
    state: SearchUiState,
    onAnimeClick: (Anime) -> Unit,
    metaText: @Composable (Anime) -> String,
    onLoadMore: () -> Unit,
    onRetrySearch: () -> Unit,
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)? = null,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryStatusLabel: @Composable (LibraryCategory) -> String,
    idleTitle: String? = null,
    idleMessage: String? = null,
    idleIcon: ImageVector? = null,
    idleTopPadding: androidx.compose.ui.unit.Dp = 44.dp,
    errorModifier: Modifier = Modifier,
    errorRetryLabel: String? = null,
    loadMoreModifier: Modifier = Modifier,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    appSearchStateVerticalListContent(
        state = state,
        onAnimeClick = onAnimeClick,
        metaText = metaText,
        onLoadMore = onLoadMore,
        onRetrySearch = onRetrySearch,
        loadMoreLabel = loadMoreLabel,
        resultsCountLabel = resultsCountLabel,
        idleTitle = idleTitle,
        idleMessage = idleMessage,
        idleIcon = idleIcon,
        idleTopPadding = idleTopPadding,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        emptyIcon = emptyIcon,
        errorModifier = errorModifier,
        errorRetryLabel = errorRetryLabel,
        loadMoreModifier = loadMoreModifier,
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
    )
}
