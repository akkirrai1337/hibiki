package org.akkirrai.hibiki.core.design.component.anime

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppLoadMoreBlock
import org.akkirrai.hibiki.core.design.component.search.AppErrorCard
import org.akkirrai.hibiki.core.design.component.search.AppSearchPlaceholder
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.SearchUiState

/** Every browse surface uses the same compact portrait grid: three columns at most. */
const val PORTRAIT_GRID_COLUMNS = 3
val PortraitGridSpacing: Dp = 12.dp

fun LazyGridScope.animePosterGridContent(
    anime: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)? = null,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    sharedCardModifier: @Composable (Anime) -> Modifier = { Modifier },
    sharedPosterModifier: @Composable (Anime) -> Modifier = { Modifier },
) {
    items(anime, key = Anime::id) { item ->
        AnimePosterCardItem(
            anime = item,
            metaText = "",
            onClick = { onAnimeClick(item) },
            onLongClick = onAnimeLongClick?.let { action -> { action(item) } },
            modifier = Modifier.fillMaxWidth(),
            titleBaseMaxLines = 2,
            titleExtraLongTitleLines = 0,
            reservedTitleLines = 2,
            showRating = true,
            posterOverlayContent = posterFooterContent?.let { footer -> { footer(item) } },
            sharedCardModifier = sharedCardModifier(item),
            sharedPosterModifier = sharedPosterModifier(item),
        )
    }
}

fun LazyGridScope.searchStatePosterGridContent(
    state: SearchUiState,
    onAnimeClick: (Anime) -> Unit,
    onLoadMore: () -> Unit,
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)? = null,
    idleTitle: String? = null,
    idleMessage: String? = null,
    idleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    errorActionLabel: String? = null,
    onErrorActionClick: (() -> Unit)? = null,
    loadMoreLoadingLabel: String? = null,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    sharedCardModifier: @Composable (Anime) -> Modifier = { Modifier },
    sharedPosterModifier: @Composable (Anime) -> Modifier = { Modifier },
) {
    fun fullWidthItem(content: @Composable () -> Unit) {
        item(span = { GridItemSpan(maxLineSpan) }) { content() }
    }

    when (state) {
        SearchUiState.Idle -> if (idleTitle != null && idleMessage != null && idleIcon != null) {
            fullWidthItem {
                AppSearchPlaceholder(title = idleTitle, message = idleMessage, icon = idleIcon)
            }
        }
        SearchUiState.Loading -> fullWidthItem {
            AppCenteredLoading(modifier = Modifier.fillMaxWidth())
        }
        SearchUiState.Empty -> fullWidthItem {
            AppSearchPlaceholder(title = emptyTitle, message = emptyMessage, icon = emptyIcon)
        }
        is SearchUiState.Error -> fullWidthItem {
            AppErrorCard(
                message = state.message,
                actionLabel = errorActionLabel,
                onActionClick = onErrorActionClick,
            )
        }
        is SearchUiState.Content -> {
            resultsCountLabel?.let { label ->
                fullWidthItem { androidx.compose.material3.Text(text = label(state.items.size)) }
            }
            animePosterGridContent(
                anime = state.items,
                onAnimeClick = onAnimeClick,
                posterFooterContent = posterFooterContent,
                sharedCardModifier = sharedCardModifier,
                sharedPosterModifier = sharedPosterModifier,
            )
            if (state.canLoadMore || state.isLoadingMore || state.loadMoreError != null) {
                fullWidthItem {
                    AppLoadMoreBlock(
                        label = loadMoreLabel,
                        onClick = onLoadMore,
                        isLoading = state.isLoadingMore,
                        errorMessage = state.loadMoreError,
                        loadingLabel = loadMoreLoadingLabel,
                    )
                }
            }
        }
    }
}
