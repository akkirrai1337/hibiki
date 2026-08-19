package org.akkirrai.hibiki.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.isPlayerSettingsOverlayActive
import org.akkirrai.hibiki.app.navigation.isPlaylistOverlayActive
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText
import org.akkirrai.hibiki.player.AppPlayerErrorOverlay

fun shouldDismissPlayerSettingsForAction(action: PlaybackSettingsAction): Boolean = false

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
    content: AppPlaybackHost,
    onEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    val handleEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit = { episode, selectionContext ->
        if (navigationState.isPlaylistOverlayActive) {
            onOverlayEvent(AppNavigationEvent.DismissOverlay)
        }
        onEpisodeSelected(episode, selectionContext)
    }
    val handleSettingsAction: (PlaybackSettingsAction) -> Unit = { action ->
        if (navigationState.isPlayerSettingsOverlayActive &&
            shouldDismissPlayerSettingsForAction(action)
        ) {
            onOverlayEvent(AppNavigationEvent.DismissOverlay)
        }
        onSettingsAction(action)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (playback != null || playbackLoading || playbackError != null) {
            content(playback, context, navigationState, playbackLoading, onDismiss, handleEpisodeSelected, handleSettingsAction, onOverlayEvent)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
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
