package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.player.AppPlayerErrorOverlay
import org.akkirrai.hibiki.shared.player.AppPlayerLoadingOverlay

/** Common full-screen overlay boundary for platform media playback surfaces. */
@Composable
fun AppPlaybackOverlayHost(
    playback: PlaybackStream?,
    context: PlaybackContext,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (PlaybackStream, PlaybackContext, AppNavigationState, () -> Unit, (WatchEpisode) -> Unit, (PlaybackSettingsAction) -> Unit, (AppNavigationEvent) -> Unit) -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    val handleEpisodeSelected: (WatchEpisode) -> Unit = { episode ->
        if (navigationState.overlays.lastOrNull() == AppOverlay.Playlist) {
            onOverlayEvent(AppNavigationEvent.DismissOverlay)
        }
        onEpisodeSelected(episode)
    }
    val handleSettingsAction: (PlaybackSettingsAction) -> Unit = { action ->
        if (
            navigationState.overlays.lastOrNull() == AppOverlay.PlayerSettings &&
            (
                action is PlaybackSettingsAction.SelectVoiceover ||
                    action is PlaybackSettingsAction.SelectPlayer ||
                    action is PlaybackSettingsAction.SelectQuality
                )
        ) {
            onOverlayEvent(AppNavigationEvent.DismissOverlay)
        }
        onSettingsAction(action)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (playback != null) {
            content(playback, context, navigationState, onDismiss, handleEpisodeSelected, handleSettingsAction, onOverlayEvent)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        AppPlayerLoadingOverlay(visible = playbackLoading)
        playbackError?.let { message ->
            AppPlayerErrorOverlay(
                message = message,
                title = appText(AppTextKey.PlayerErrorTitle),
                retryLabel = appText(AppTextKey.PlayerRetry),
                onRetry = onRetry,
            )
        }
    }
}
