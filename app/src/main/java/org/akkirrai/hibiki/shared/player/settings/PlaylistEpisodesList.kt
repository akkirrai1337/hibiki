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
import org.akkirrai.hibiki.shared.player.model.WatchEpisode

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
            val headlineText = headline(episode)
            PlaylistEpisodeRow(
                headline = headlineText,
                // Some sources report a generic "Episode N" as the title when the episode has
                // no real name -- that's just the headline again, so only show it as a distinct
                // subtitle when it actually differs.
                subtitle = episode.title?.takeIf { it.isNotBlank() && !it.equals(headlineText, ignoreCase = true) },
                selected = episode.id == currentEpisodeId,
                onClick = { onEpisodeClick(episode.id) },
            )
        }
    }
}
