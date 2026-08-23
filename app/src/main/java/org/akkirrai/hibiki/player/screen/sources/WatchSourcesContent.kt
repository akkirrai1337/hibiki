package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

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

@Composable
fun AppWatchSourcesStateContent(
    state: WatchSourcesScreenState,
    emptyTitle: String,
    emptyMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<WatchSource>) -> Unit,
) {
    when {
        state.errorMessage != null -> WatchEmptyState(
            title = emptyTitle,
            message = state.errorMessage,
            icon = Icons.Outlined.PlayCircleOutline,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        state.items.isEmpty() && state.isLoading -> AppCenteredLoading(modifier = modifier.fillMaxSize())
        state.items.isEmpty() -> WatchEmptyState(
            title = emptyTitle,
            message = emptyMessage,
            icon = Icons.Outlined.SubtitlesOff,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        else -> content(state.items)
    }
}

@Composable
internal fun WatchSourcesDestinationContent(
    state: WatchSourcesScreenState,
    navigationLocked: Boolean,
    onWatchRetry: () -> Unit,
    onWatchSourceClick: (WatchSource) -> Unit,
    onWatchLoadMore: () -> Unit,
    listContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    AppWatchSourcesContent(
        state = state,
        emptyTitle = appText(AppTextKey.WatchSourcesEmptyTitle),
        emptyMessage = appText(AppTextKey.WatchSourcesEmptyMessage),
        retryLabel = appText(AppTextKey.SearchRetry),
        episodeLabel = appText(AppTextKey.EpisodesShort),
        loadMoreLabel = appText(AppTextKey.WatchSourcesLoadMore),
        enabled = !state.isLoading && !navigationLocked,
        onRetry = onWatchRetry,
        onSourceClick = { source ->
            if (!navigationLocked) onWatchSourceClick(source)
        },
        onLoadMore = onWatchLoadMore,
        listContentPadding = listContentPadding,
        modifier = modifier.fillMaxSize(),
    )
}
