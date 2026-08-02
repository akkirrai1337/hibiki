package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.model.WatchSource

@Composable
fun AppWatchSourcesContent(
    state: WatchSourcesScreenState,
    emptyTitle: String,
    emptyMessage: String,
    retryLabel: String,
    episodeLabel: String,
    loadMoreLabel: String,
    enabled: Boolean,
    onRetry: () -> Unit,
    onSourceClick: (WatchSource) -> Unit,
    onLoadMore: () -> Unit,
    listContentPadding: PaddingValues? = null,
    modifier: Modifier = Modifier,
) {
    AppWatchSourcesStateContent(
        state = state,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
    ) { sources ->
        WatchSourcesList(
            sources = sources,
            enabled = enabled,
            horizontalPadding = UiDimens.ScreenPadding,
            episodeSummary = { source ->
                source.episodeCount?.let { count ->
                    formatWatchSourceEpisodeSummary(
                        episodeCount = count,
                        episodeLabel = episodeLabel,
                    )
                }
            },
            onSourceClick = onSourceClick,
            hasMoreItems = state.hasMoreItems,
            loadMoreLabel = loadMoreLabel,
            isLoadingMore = state.isLoadingMore,
            onLoadMore = onLoadMore,
            isRefreshing = state.isRefreshing(),
            contentPadding = listContentPadding,
        )
    }
}
