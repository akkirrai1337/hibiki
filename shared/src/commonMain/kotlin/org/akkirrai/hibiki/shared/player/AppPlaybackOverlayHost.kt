package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent

/** Common full-screen overlay boundary for platform media playback surfaces. */
@Composable
fun AppPlaybackOverlayHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onDismiss: () -> Unit,
    content: @Composable (PlaybackStream, PlaybackContext, () -> Unit, (WatchEpisode) -> Unit, (PlaybackSettingsAction) -> Unit, (AppNavigationEvent) -> Unit) -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content(playback, context, onDismiss, onEpisodeSelected, onSettingsAction, onOverlayEvent)
    }
}
