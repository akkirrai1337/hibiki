package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.player.model.WatchEpisode
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AppEpisodesContent(
    result: EpisodesUiState,
    sourceTitle: String,
    emptyMessage: String,
    retryLabel: String,
    showMoreLabel: String,
    onRetry: () -> Unit,
    episodeContent: @Composable (WatchEpisode, androidx.compose.foundation.shape.RoundedCornerShape) -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
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
            showMoreLabel = showMoreLabel,
            headerContent = headerContent,
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
    showMoreLabel: String,
    headerContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    // Trailing, non-episode row (e.g. the not-yet-released next episode's countdown) -- shaped as
    // if it were one more item in the list, so it visually continues the same rounded group.
    upcomingContent: (@Composable (androidx.compose.foundation.shape.RoundedCornerShape) -> Unit)? = null,
) {
    var visibleCount by remember(episodes.size, episodes.firstOrNull()?.id, episodes.lastOrNull()?.id) {
        mutableIntStateOf(EpisodesPageSize.coerceAtMost(episodes.size))
    }
    val visibleEpisodes = episodes.take(visibleCount)
    val hasMore = visibleCount < episodes.size
    val itemShape = RoundedCornerShape(EpisodeRowDefaultCornerRadius)
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
        if (headerContent != null) {
            item(key = "episodes_header") {
                headerContent()
            }
        }
        itemsIndexed(visibleEpisodes, key = { _, episode -> episode.id }) { _, episode ->
            episodeContent(episode, itemShape)
        }
        if (hasMore) {
            item(key = "show_more_episodes") {
                ShowMoreEpisodesRow(
                    label = showMoreLabel,
                    shape = itemShape,
                    onClick = { visibleCount = (visibleCount + EpisodesPageSize).coerceAtMost(episodes.size) },
                )
            }
        }
        if (upcomingContent != null) {
            item(key = "upcoming_episode") {
                upcomingContent(itemShape)
            }
        }
    }
}

@Composable
private fun ShowMoreEpisodesRow(
    label: String,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = EpisodeRowHorizontalPadding,
                vertical = EpisodeRowVerticalPadding,
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
