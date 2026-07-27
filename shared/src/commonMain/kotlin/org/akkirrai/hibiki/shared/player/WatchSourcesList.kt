package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreBlock
import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.source.sourceItemShape

@Composable
fun WatchSourcesList(
    sources: List<WatchSource>,
    enabled: Boolean,
    onSourceClick: (WatchSource) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    episodeSummary: @Composable (WatchSource) -> String? = { source ->
        source.episodeCount?.let { count -> "· $count" }
    },
    hasMoreItems: Boolean = false,
    loadMoreLabel: String = "",
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(sources, key = { _, source -> source.sourceId }) { index, source ->
            WatchSourceRow(
                title = source.title,
                episodeSummary = episodeSummary(source),
                enabled = enabled,
                horizontalPadding = horizontalPadding,
                shape = sourceItemShape(index, sources.size),
                onClick = { onSourceClick(source) },
            )
        }
        if (hasMoreItems) {
            item {
                AppLoadMoreBlock(
                    label = loadMoreLabel,
                    onClick = onLoadMore,
                    isLoading = isLoadingMore,
                    modifier = Modifier.padding(
                        horizontal = horizontalPadding,
                        vertical = 18.dp,
                    ),
                )
            }
        }
        if (isRefreshing) {
            item {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                ) { AppCenteredLoading() }
            }
        }
    }
}
