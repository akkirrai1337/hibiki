package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.AppWatchSourcesContent
import org.akkirrai.hibiki.player.WatchSourcesScreenState
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

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
