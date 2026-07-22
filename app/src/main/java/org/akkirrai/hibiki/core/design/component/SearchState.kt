package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

@Composable
fun AppSearchPlaceholder(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 44.dp,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = 24.dp, end = 24.dp),
        iconSlot = {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(26.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
fun AppErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onActionClick != null) {
                FilledTonalButton(onClick = onActionClick) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

fun LazyGridScope.searchStateGridContent(
    state: SearchUiState,
    onAnimeClick: (Anime) -> Unit,
    metaText: (Anime) -> String,
    onLoadMore: () -> Unit,
    loadMoreLabel: String,
    modifier: Modifier = Modifier,
    idleTitle: String? = null,
    idleMessage: String? = null,
    idleIcon: ImageVector? = null,
    idleTopPadding: Dp = 44.dp,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: ImageVector,
    emptyTopPadding: Dp = 44.dp,
    errorModifier: Modifier = Modifier,
    errorActionLabel: String? = null,
    onErrorActionClick: (() -> Unit)? = null,
    loadMoreLoadingLabel: String? = null,
    loadMoreModifier: Modifier = Modifier,
) {
    when (state) {
        SearchUiState.Idle -> {
            if (idleTitle != null && idleMessage != null && idleIcon != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppSearchPlaceholder(
                        title = idleTitle,
                        message = idleMessage,
                        icon = idleIcon,
                        modifier = modifier,
                        topPadding = idleTopPadding,
                    )
                }
            }
        }

        SearchUiState.Loading -> {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppCenteredLoading(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(top = 44.dp),
                )
            }
        }

        SearchUiState.Empty -> {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppSearchPlaceholder(
                    title = emptyTitle,
                    message = emptyMessage,
                    icon = emptyIcon,
                    modifier = modifier,
                    topPadding = emptyTopPadding,
                )
            }
        }

        is SearchUiState.Error -> {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppErrorCard(
                    message = state.message,
                    modifier = errorModifier,
                    actionLabel = errorActionLabel,
                    onActionClick = onErrorActionClick,
                )
            }
        }

        is SearchUiState.Content -> {
            animeSearchResultsContent(
                items = state.items,
                canLoadMore = state.canLoadMore,
                isLoadingMore = state.isLoadingMore,
                onAnimeClick = onAnimeClick,
                metaText = metaText,
                onLoadMore = onLoadMore,
                loadMoreLabel = loadMoreLabel,
                loadMoreErrorMessage = state.loadMoreError,
                loadMoreLoadingLabel = loadMoreLoadingLabel,
                loadMoreModifier = loadMoreModifier,
            )
        }
    }
}
