package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.model.WatchEpisode

@Composable
fun PlaylistEpisodesList(
    currentEpisodeId: String,
    episodes: List<WatchEpisode>,
    maxHeight: Dp,
    horizontalPadding: Dp,
    headline: @Composable (WatchEpisode) -> String,
    onEpisodeClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            top = PlaylistEpisodesListTopPadding,
            end = horizontalPadding,
            bottom = PlaylistEpisodesListBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PlaylistEpisodesListItemGap),
    ) {
        items(episodes, key = WatchEpisode::id) { episode ->
            PlaylistEpisodeRow(
                headline = headline(episode),
                subtitle = episode.title,
                selected = episode.id == currentEpisodeId,
                onClick = { onEpisodeClick(episode.id) },
            )
        }
    }
}
