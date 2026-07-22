package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState
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
    metaContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    posterFooterContent: (@Composable () -> Unit)? = null,
) {
    org.akkirrai.hibiki.shared.design.component.AppVerticalAnimeListItem(
        anime = anime,
        metaText = metaText,
        onClick = onClick,
        modifier = modifier,
        posterWidth = posterWidth,
        descriptionMaxLines = descriptionMaxLines,
        trailingContent = trailingContent,
        metaContent = metaContent,
        supportingContent = supportingContent,
        trailingIcon = Icons.Outlined.ChevronRight,
        posterContent = {
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
        },
        posterFooterContent = posterFooterContent,
    )
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
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
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
                LaunchedEffect(anime.id) {
                    onItemVisible?.invoke(anime)
                }
                VerticalAnimeListItem(
                    anime = anime,
                    metaText = metaText(anime),
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier.fillMaxWidth(),
                    posterFooterContent = posterFooterContent?.let { footer -> { footer(anime) } },
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

/** Shared list content for every screen that presents anime as vertical cards. */
fun LazyListScope.verticalAnimeListContent(
    items: List<Anime>,
    metaText: @Composable (Anime) -> String,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    items(items, key = Anime::id) { anime ->
        LaunchedEffect(anime.id) {
            onItemVisible?.invoke(anime)
        }
        VerticalAnimeListItem(
            anime = anime,
            metaText = metaText(anime),
            onClick = { onAnimeClick(anime) },
            modifier = modifier.fillMaxWidth(),
            posterFooterContent = posterFooterContent?.let { footer -> { footer(anime) } },
        )
    }
}
