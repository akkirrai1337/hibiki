package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.WatchEpisode

@Composable
fun AppEpisodesContent(
    result: EpisodesUiState,
    sourceTitle: String,
    emptyMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    episodeContent: @Composable (WatchEpisode, androidx.compose.foundation.shape.RoundedCornerShape) -> Unit,
    listContentPadding: PaddingValues? = null,
    modifier: Modifier = Modifier,
) {
    AppEpisodesStateContent(
        result = result,
        sourceTitle = sourceTitle,
        emptyMessage = emptyMessage,
        retryLabel = retryLabel,
        onRetry = onRetry,
        modifier = modifier,
    ) { episodes ->
        EpisodesList(
            episodes = episodes,
            episodeContent = episodeContent,
            contentPadding = listContentPadding,
        )
    }
}
