package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
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
