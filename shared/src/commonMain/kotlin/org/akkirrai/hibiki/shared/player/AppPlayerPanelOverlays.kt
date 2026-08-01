package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.WatchEpisode

/** Common playlist/settings overlay orchestration around a platform media surface. */
@Composable
fun AppPlayerPanelOverlays(
    playlistVisible: Boolean,
    settingsVisible: Boolean,
    currentEpisodeId: String,
    episodes: List<WatchEpisode>,
    episodeHeadline: @Composable (WatchEpisode) -> String,
    onDismissPlaylist: () -> Unit,
    onDismissSettings: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    nowMs: () -> Long,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit,
    settingsContent: @Composable ((() -> Unit)) -> Unit,
    skipVisible: Boolean,
    controlsVisible: Boolean,
    skipCountdownSeconds: Int,
    autoSkipEnabled: Boolean,
    skipLabel: String,
    watchLabel: String,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
    skipModifier: Modifier = Modifier,
) {
    AppPlayerPlaylistLayer(
        visible = playlistVisible,
        currentEpisodeId = currentEpisodeId,
        episodes = episodes,
        headline = episodeHeadline,
        onDismissRequest = onDismissPlaylist,
        onEpisodeClick = onEpisodeClick,
        nowMs = nowMs,
        backHandler = backHandler,
    )
    if (settingsVisible) {
        AppPlayerSettingsLayer(
            onDismissRequest = onDismissSettings,
            nowMs = nowMs,
            backHandler = backHandler,
            content = settingsContent,
        )
    }
    AppPlayerSkipSegmentLayer(
        visible = skipVisible,
        controlsVisible = controlsVisible,
        countdownSeconds = skipCountdownSeconds,
        maxCountdownSeconds = DefaultSkipSegmentCountdownSeconds,
        autoSkipEnabled = autoSkipEnabled,
        skipLabel = skipLabel,
        watchLabel = watchLabel,
        onSkipClick = onSkipClick,
        onWatchClick = onWatchClick,
        modifier = skipModifier,
    )
}
