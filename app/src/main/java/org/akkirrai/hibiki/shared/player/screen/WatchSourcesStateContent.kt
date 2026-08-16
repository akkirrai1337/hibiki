package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.shared.player.model.WatchSource

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
