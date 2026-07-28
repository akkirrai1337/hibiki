package org.akkirrai.hibiki.shared.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder
import org.akkirrai.hibiki.shared.design.component.appSearchStateVerticalListContent
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

fun LazyListScope.appHomeSearchResultsContent(
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
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        emptyIcon = emptyIcon,
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
