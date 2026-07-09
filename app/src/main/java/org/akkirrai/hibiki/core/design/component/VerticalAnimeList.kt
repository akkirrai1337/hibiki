package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.model.buildCardMeta

@Composable
fun VerticalAnimeListItem(
    anime: Anime,
    metaText: String = anime.buildCardMeta(announcementLabel = ""),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = 104.dp,
    descriptionMaxLines: Int = 5,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(posterWidth)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            PosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        var titleLineCount by remember(anime.id, anime.title) { mutableIntStateOf(1) }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                AnimeTitleText(
                    text = anime.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    baseMaxLines = 2,
                    extraLongTitleLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (titleLineCount != layout.lineCount) {
                            titleLineCount = layout.lineCount
                        }
                    },
                )
                trailingContent?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    it()
                }
            }

            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            anime.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = descriptionMaxLines.coerceAtMost(
                        (3 + (3 - titleLineCount).coerceAtLeast(0)).coerceAtLeast(1)
                    ),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun LazyListScope.searchStateVerticalListContent(
    state: SearchUiState,
    onAnimeClick: (Anime) -> Unit,
    metaText: (Anime) -> String,
    onLoadMore: () -> Unit,
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)? = null,
    modifier: Modifier = Modifier,
    idleTitle: String? = null,
    idleMessage: String? = null,
    idleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    idleTopPadding: Dp = 44.dp,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTopPadding: Dp = 44.dp,
    errorModifier: Modifier = Modifier,
    errorActionLabel: String? = null,
    onErrorActionClick: (() -> Unit)? = null,
    loadMoreLoadingLabel: String? = null,
    loadMoreModifier: Modifier = Modifier,
) {
    when (state) {
        SearchUiState.Idle -> if (idleTitle != null && idleMessage != null && idleIcon != null) {
            item {
                AppSearchPlaceholder(
                    title = idleTitle,
                    message = idleMessage,
                    icon = idleIcon,
                    modifier = modifier,
                    topPadding = idleTopPadding,
                )
            }
        }
        SearchUiState.Loading -> item {
            AppCenteredLoading(
                modifier = modifier.fillMaxWidth().padding(top = 44.dp),
            )
        }
        SearchUiState.Empty -> item {
            AppSearchPlaceholder(
                title = emptyTitle,
                message = emptyMessage,
                icon = emptyIcon,
                modifier = modifier,
                topPadding = emptyTopPadding,
            )
        }
        is SearchUiState.Error -> item {
            AppErrorCard(
                message = state.message,
                modifier = errorModifier,
                actionLabel = errorActionLabel,
                onActionClick = onErrorActionClick,
            )
        }
        is SearchUiState.Content -> {
            resultsCountLabel?.let { label ->
                item {
                    Text(
                        text = label(state.items.size),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            items(state.items, key = { it.id }) { anime ->
                VerticalAnimeListItem(
                    anime = anime,
                    metaText = metaText(anime),
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.canLoadMore || state.isLoadingMore || state.loadMoreError != null) {
                item {
                    AppLoadMoreBlock(
                        label = loadMoreLabel,
                        onClick = onLoadMore,
                        isLoading = state.isLoadingMore,
                        errorMessage = state.loadMoreError,
                        loadingLabel = loadMoreLoadingLabel,
                        modifier = loadMoreModifier,
                    )
                }
            }
        }
    }
}
