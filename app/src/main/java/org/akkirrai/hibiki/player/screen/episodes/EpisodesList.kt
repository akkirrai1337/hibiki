package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.core.source.sourceItemShape

@Composable
fun EpisodesList(
    episodes: List<WatchEpisode>,
    episodeContent: @Composable (WatchEpisode, androidx.compose.foundation.shape.RoundedCornerShape) -> Unit,
    contentPadding: PaddingValues? = null,
    modifier: Modifier = Modifier,
    // Trailing, non-episode row (e.g. the not-yet-released next episode's countdown). It's a
    // standalone pill, not one more card in the same full-width group, so the real episode rows'
    // shapes are computed as if it weren't there -- the last real episode still gets a proper
    // bottom-rounded "end of the group" shape.
    upcomingContent: (@Composable () -> Unit)? = null,
) {
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
            episodeContent(episode, sourceItemShape(index, episodes.size))
        }
        if (upcomingContent != null) {
            item(key = "upcoming_episode") {
                upcomingContent()
            }
        }
    }
}
