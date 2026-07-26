package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
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
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

/**
 * Platform-neutral [LazyListScope] content for a search screen that renders
 * [SearchUiState] states. Used on Android and Desktop.
 *
 * @param state the current search state.
 * @param onAnimeClick callback when an anime card is tapped.
 * @param metaText composable function that produces the metadata string for each anime.
 * @param onLoadMore callback to load the next page.
 * @param onRetrySearch callback to retry a failed search.
 * @param loadMoreLabel label for the "load more" button.
 * @param resultsCountLabel optional composable that formats the result count header.
 * @param modifier modifier applied to non-content state blocks (Idle, Empty, Loading).
 * @param idleTitle title shown in the Idle state.
 * @param idleMessage subtitle shown in the Idle state.
 * @param idleIcon icon shown in the Idle state.
 * @param idleTopPadding top padding for the Idle content.
 * @param emptyTitle title shown when results are empty.
 * @param emptyMessage subtitle shown when results are empty.
 * @param emptyIcon icon shown in the Empty state.
 * @param emptyTopPadding top padding for the Empty content.
 * @param errorModifier modifier applied to the error card.
 * @param errorRetryLabel label for the retry button in the error state.
 * @param loadMoreLoadingLabel optional label shown next to the spinner while loading more.
 * @param loadMoreModifier modifier applied to the load-more block.
 * @param posterContent slot for the anime poster image (platform-specific loader).
 * @param posterFooterContent optional slot for overlay content at the bottom of each poster.
 * @param onItemVisible optional visibility callback for each anime item.
 */
fun LazyListScope.appSearchStateVerticalListContent(
    state: SearchUiState,
    onAnimeClick: (Anime) -> Unit,
    metaText: @Composable (Anime) -> String,
    onLoadMore: () -> Unit,
    onRetrySearch: () -> Unit,
    loadMoreLabel: String,
    resultsCountLabel: (@Composable (Int) -> String)? = null,
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
    errorRetryLabel: String? = null,
    loadMoreLoadingLabel: String? = null,
    loadMoreModifier: Modifier = Modifier,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    posterFooterContent: (@Composable (Anime) -> Unit)? = null,
    onItemVisible: ((Anime) -> Unit)? = null,
) {
    when (state) {
        SearchUiState.Idle -> {
            if (idleTitle != null && idleMessage != null && idleIcon != null) {
                item {
                    AppMessageState(
                        title = idleTitle,
                        message = idleMessage,
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(top = idleTopPadding, start = 24.dp, end = 24.dp),
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
                                    imageVector = idleIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }

        SearchUiState.Loading -> item {
            AppCenteredLoading(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp),
            )
        }

        SearchUiState.Empty -> item {
            AppMessageState(
                title = emptyTitle,
                message = emptyMessage,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = emptyTopPadding, start = 24.dp, end = 24.dp),
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
                            imageVector = emptyIcon,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        is SearchUiState.Error -> item {
            Card(
                modifier = errorModifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (errorRetryLabel != null) {
                        FilledTonalButton(onClick = onRetrySearch) {
                            Text(text = errorRetryLabel)
                        }
                    }
                }
            }
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

            appVerticalAnimeListContent(
                items = state.items,
                metaText = metaText,
                onAnimeClick = onAnimeClick,
                modifier = modifier,
                posterContent = posterContent,
                posterFooterContent = posterFooterContent,
                onItemVisible = onItemVisible,
            )

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
