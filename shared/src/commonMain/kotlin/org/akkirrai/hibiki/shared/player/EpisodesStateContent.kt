package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading

@Composable
fun AppEpisodesStateContent(
    result: EpisodesUiState,
    sourceTitle: String,
    emptyMessage: String,
    retryLabel: String,
    icon: ImageVector,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<WatchEpisode>) -> Unit,
) {
    when (result) {
        EpisodesUiState.Loading -> AppCenteredLoading(modifier = modifier.fillMaxSize())
        EpisodesUiState.Empty -> WatchEmptyState(
            title = sourceTitle,
            message = emptyMessage,
            icon = icon,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        is EpisodesUiState.Error -> WatchEmptyState(
            title = sourceTitle,
            message = result.message,
            icon = icon,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        is EpisodesUiState.Content -> content(result.items)
    }
}
