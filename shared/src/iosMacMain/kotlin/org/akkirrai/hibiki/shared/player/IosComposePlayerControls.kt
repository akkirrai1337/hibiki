package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream

@Composable
internal fun IosComposePlayerControls(
    transport: PlaybackTransport,
    playback: PlaybackStream,
    context: PlaybackContext,
    scaleMode: VideoScaleMode,
    onBack: () -> Unit,
    onScaleClick: () -> Unit = {},
    scaleContentDescription: String? = null,
    playlistEnabled: Boolean = false,
    onPlaylistClick: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    settingsContentDescription: String? = null,
    onLockClick: () -> Unit = {},
    lockContentDescription: String? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    pictureInPictureEnabled: Boolean = false,
    onPictureInPictureClick: () -> Unit = {},
    pictureInPictureContentDescription: String? = null,
) {
    val layoutEnvironment = LocalAppLayoutEnvironment.current
    AppPlaybackControls(
            transport = transport,
            playback = playback,
            context = context,
            scaleMode = scaleMode,
            onScaleClick = onScaleClick,
            scaleContentDescription = scaleContentDescription,
            onBack = onBack,
            playlistEnabled = playlistEnabled,
            onPlaylistClick = onPlaylistClick,
            hasPreviousEpisode = hasPreviousEpisode,
            hasNextEpisode = hasNextEpisode,
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
            onSettingsClick = onSettingsClick,
            settingsContentDescription = settingsContentDescription,
            onLockClick = onLockClick,
            lockContentDescription = lockContentDescription,
            pictureInPictureEnabled = pictureInPictureEnabled,
            onPictureInPictureClick = onPictureInPictureClick,
            pictureInPictureContentDescription = pictureInPictureContentDescription,
            onControlsVisibilityChanged = onControlsVisibilityChanged,
            topContentInset = if (layoutEnvironment.isProvided) layoutEnvironment.topSystemInset else 0.dp,
        )
}
