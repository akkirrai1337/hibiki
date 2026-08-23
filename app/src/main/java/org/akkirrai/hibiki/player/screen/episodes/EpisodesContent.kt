package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.core.source.sourceItemShape

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
    upcomingContent: (@Composable (androidx.compose.foundation.shape.RoundedCornerShape) -> Unit)? = null,
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
            upcomingContent = upcomingContent,
        )
    }
}

@Composable
private fun AppEpisodesStateContent(
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

@Composable
private fun EpisodesList(
    episodes: List<WatchEpisode>,
    episodeContent: @Composable (WatchEpisode, androidx.compose.foundation.shape.RoundedCornerShape) -> Unit,
    contentPadding: PaddingValues? = null,
    modifier: Modifier = Modifier,
    // Trailing, non-episode row (e.g. the not-yet-released next episode's countdown) -- shaped as
    // if it were one more item in the list, so it visually continues the same rounded group.
    upcomingContent: (@Composable (androidx.compose.foundation.shape.RoundedCornerShape) -> Unit)? = null,
) {
    val itemCount = episodes.size + if (upcomingContent != null) 1 else 0
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding ?: PaddingValues(
            start = EpisodesListHorizontalPadding,
            end = EpisodesListHorizontalPadding,
            top = EpisodesListTopPadding,
            bottom = EpisodesListBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(EpisodesListItemGap),
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            episodeContent(episode, sourceItemShape(index, itemCount))
        }
        if (upcomingContent != null) {
            item(key = "upcoming_episode") {
                upcomingContent(sourceItemShape(episodes.size, itemCount))
            }
        }
    }
}
