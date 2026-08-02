package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

data class AppWatchSourcesScreenLabels(
    val emptyTitle: String,
    val emptyMessage: String,
    val retry: String,
    val loadMore: String,
    val episodesShort: String,
)

data class AppWatchSourcesScreenIcons(
    val back: @Composable () -> Unit,
    val error: ImageVector,
    val empty: ImageVector,
)

/** Stateless sources picker shared by the Android, iOS and desktop hosts. */
@Composable
fun AppWatchSourcesScreen(
    state: WatchSourcesScreenState,
    labels: AppWatchSourcesScreenLabels,
    icons: AppWatchSourcesScreenIcons,
    enabled: Boolean,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSourceClick: (org.akkirrai.hibiki.shared.model.WatchSource) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = UiDimens.ScreenPadding,
    episodeSummary: @Composable (org.akkirrai.hibiki.shared.model.WatchSource) -> String? = { source ->
        source.episodeCount?.let { count -> "· $count ${labels.episodesShort}" }
    },
) {
    WatchScreenScaffold(
        onBackClick = onBackClick,
        backEnabled = enabled,
        backContentDescription = null,
        modifier = modifier,
    ) { contentPadding ->
        when {
            state.errorMessage != null -> WatchEmptyState(
                title = labels.emptyTitle,
                message = state.errorMessage.orEmpty(),
                icon = icons.error,
                retryLabel = labels.retry,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            state.items.isEmpty() && state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.items.isEmpty() -> WatchEmptyState(
                title = labels.emptyTitle,
                message = labels.emptyMessage,
                icon = icons.empty,
                retryLabel = labels.retry,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            else -> WatchSourcesList(
                sources = state.items,
                enabled = enabled,
                horizontalPadding = horizontalPadding,
                episodeSummary = episodeSummary,
                onSourceClick = onSourceClick,
                hasMoreItems = state.hasMoreItems,
                loadMoreLabel = labels.loadMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                contentPadding = contentPadding,
            )
        }
    }
}
