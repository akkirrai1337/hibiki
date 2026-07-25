package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.model.WatchSource

@Composable
fun WatchSourcesList(
    sources: List<WatchSource>,
    enabled: Boolean,
    onSourceClick: (WatchSource) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    episodeSummary: @Composable (WatchSource) -> String? = { source ->
        source.episodeCount?.let { count -> "· $count" }
    },
    loadMoreContent: (@Composable () -> Unit)? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 68.dp, bottom = 12.dp),
    ) {
        items(sources, key = WatchSource::sourceId) { source ->
            WatchSourceRow(
                title = source.title,
                episodeSummary = episodeSummary(source),
                enabled = enabled,
                horizontalPadding = horizontalPadding,
                onClick = { onSourceClick(source) },
            )
        }
        loadMoreContent?.let { content ->
            item { content() }
        }
        loadingContent?.let { content ->
            item {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                ) { content() }
            }
        }
    }
}
