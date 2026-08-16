package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.design.component.state.AppCenteredLoading

@Composable
fun AppEpisodesStateContent(
    result: EpisodesUiState,
    sourceTitle: String,
    emptyMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (List<WatchEpisode>) -> Unit,
) {
    when (result) {
        EpisodesUiState.Loading -> AppCenteredLoading(modifier = modifier.fillMaxSize())
        EpisodesUiState.Empty -> WatchEmptyState(
            title = sourceTitle,
            message = emptyMessage,
            icon = Icons.Outlined.VideoLibrary,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        is EpisodesUiState.Error -> WatchEmptyState(
            title = sourceTitle,
            message = result.message,
            icon = Icons.Outlined.VideoLibrary,
            retryLabel = retryLabel,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        is EpisodesUiState.Content -> content(result.items)
    }
}
