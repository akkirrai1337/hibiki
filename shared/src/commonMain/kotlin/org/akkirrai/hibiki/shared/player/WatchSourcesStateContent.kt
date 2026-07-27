package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading
import org.akkirrai.hibiki.shared.model.WatchSource

@Composable
fun AppWatchSourcesStateContent(
    state: WatchSourcesScreenState,
    emptyTitle: String,
    emptyMessage: String,
    errorIcon: ImageVector,
    emptyIcon: ImageVector,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<WatchSource>) -> Unit,
) {
    when {
        state.errorMessage != null -> WatchEmptyState(
            title = emptyTitle,
            message = state.errorMessage,
            icon = errorIcon,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        state.items.isEmpty() && state.isLoading -> AppCenteredLoading(modifier = modifier.fillMaxSize())
        state.items.isEmpty() -> WatchEmptyState(
            title = emptyTitle,
            message = emptyMessage,
            icon = emptyIcon,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        else -> content(state.items)
    }
}
