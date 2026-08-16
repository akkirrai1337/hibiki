package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.player.model.WatchEpisode

@Composable
fun AppPlaylistBottomSheet(
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
