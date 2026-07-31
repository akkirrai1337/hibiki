package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream

@Composable
internal fun IosComposePlayerControls(
    session: IosPlayerSession,
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onScaleClick: () -> Unit = { session.scaleMode = session.scaleMode.next() },
    playlistEnabled: Boolean = false,
    onPlaylistClick: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    settingsContentDescription: String? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
) {
    AppPlaybackControls(
        transport = session.transport,
        playback = playback,
        context = context,
        scaleMode = session.scaleMode,
        onScaleClick = onScaleClick,
        onBack = onBack,
        playlistEnabled = playlistEnabled,
        onPlaylistClick = onPlaylistClick,
        hasPreviousEpisode = hasPreviousEpisode,
        hasNextEpisode = hasNextEpisode,
        onPreviousEpisode = onPreviousEpisode,
        onNextEpisode = onNextEpisode,
        onSettingsClick = onSettingsClick,
        settingsContentDescription = settingsContentDescription,
        onControlsVisibilityChanged = onControlsVisibilityChanged,
    )
}
