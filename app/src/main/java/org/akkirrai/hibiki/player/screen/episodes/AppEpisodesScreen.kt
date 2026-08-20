package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.akkirrai.hibiki.player.model.WatchEpisode

data class AppEpisodesScreenLabels(
    val sourceTitle: String,
    val emptyMessage: String,
    val retry: String,
    val download: String,
)

data class AppEpisodesScreenIcons(
    val back: @Composable () -> Unit,
    val empty: ImageVector,
)

/** Shared episodes state/list shell; download operations remain host callbacks. */
@Composable
fun AppEpisodesScreen(
    state: EpisodesScreenState,
    labels: AppEpisodesScreenLabels,
    icons: AppEpisodesScreenIcons,
    enabled: Boolean,
    downloadControlsVisible: Boolean,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onToggleDownloadControls: () -> Unit,
    downloadToggleContent: @Composable (modifier: Modifier, visible: Boolean, label: String, onClick: () -> Unit) -> Unit,
    episodeContent: @Composable (WatchEpisode, RoundedCornerShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    WatchScreenScaffold(
        onBackClick = onBackClick,
        backEnabled = enabled,
        backContentDescription = null,
        title = null,
        modifier = modifier,
    ) { contentPadding ->
        downloadToggleContent(
            Modifier.align(Alignment.TopEnd),
            downloadControlsVisible,
            labels.download,
            onToggleDownloadControls,
        )
        when (val result = state.result) {
            EpisodesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            EpisodesUiState.Empty -> WatchEmptyState(
                title = labels.sourceTitle,
                message = labels.emptyMessage,
                icon = icons.empty,
                retryLabel = labels.retry,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            is EpisodesUiState.Error -> WatchEmptyState(
                title = labels.sourceTitle,
                message = result.message,
                icon = icons.empty,
                retryLabel = labels.retry,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            is EpisodesUiState.Content -> EpisodesList(
                episodes = result.items,
                episodeContent = episodeContent,
                contentPadding = contentPadding,
            )
        }
    }
}
