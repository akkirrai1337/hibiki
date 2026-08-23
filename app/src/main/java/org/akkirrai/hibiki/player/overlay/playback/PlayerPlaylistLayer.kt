package org.akkirrai.hibiki.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.player.model.WatchEpisode

@Composable
fun AppPlayerPlaylistLayer(
    visible: Boolean,
    currentEpisodeId: String,
    episodes: List<WatchEpisode>,
    headline: @Composable (WatchEpisode) -> String,
    onDismissRequest: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
) {
    if (!visible) return

    AppPlayerOverlayPanel(
        onDismissRequest = onDismissRequest,
        widthFraction = PlayerPlaylistPanelWidthFraction,
        maxWidth = PlayerPlaylistPanelMaxWidth,
        swipeToDismissEnabled = false,
        nowMs = nowMs,
        backHandler = backHandler,
    ) { dismissPanel ->
        AppPlaylistBottomSheet(
            currentEpisodeId = currentEpisodeId,
            episodes = episodes,
            headline = headline,
            onEpisodeClick = { episodeId ->
                dismissPanel()
                onEpisodeClick(episodeId)
            },
        )
    }
}

@Composable
private fun AppPlaylistBottomSheet(
    currentEpisodeId: String,
    episodes: List<WatchEpisode>,
    headline: @Composable (WatchEpisode) -> String,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlaylistEpisodesList(
            currentEpisodeId = currentEpisodeId,
            episodes = episodes,
            maxHeight = PlaylistBottomSheetMaxHeight,
            horizontalPadding = UiDimens.ScreenPadding,
            headline = headline,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

@Composable
private fun PlaylistEpisodesList(
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

@Composable
private fun PlaylistEpisodeRow(
    headline: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val titleColor = if (selected) Color.White else Color.White.copy(alpha = 0.92f)
    val subtitleColor = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(PlaylistEpisodeRowCornerRadius)),
        color = if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = PlaylistEpisodeRowHorizontalPadding,
                vertical = PlaylistEpisodeRowVerticalPadding,
            ),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = PlaylistEpisodeRowSubtitleTopPadding),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
