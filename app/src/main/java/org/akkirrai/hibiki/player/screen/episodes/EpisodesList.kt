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
    }
}
