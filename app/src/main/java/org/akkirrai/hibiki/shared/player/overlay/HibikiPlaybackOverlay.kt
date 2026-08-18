package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.player.AppPlaybackOverlayHost
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction

@Composable
internal fun HibikiPlaybackOverlay(
    route: PlaybackRoute?,
    pendingContext: PlaybackContext?,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
    content: @Composable (
        PlaybackStream?,
        PlaybackContext,
        AppNavigationState,
        Boolean,
        () -> Unit,
        (WatchEpisode, PlaybackContext) -> Unit,
        (PlaybackSettingsAction) -> Unit,
        (AppNavigationEvent) -> Unit,
    ) -> Unit,
) {
    AppPlaybackOverlayHost(
        playback = route?.playback,
        context = route?.context ?: requireNotNull(pendingContext),
        navigationState = navigationState,
        playbackLoading = playbackLoading,
        playbackError = playbackError,
        onRetry = onRetry,
        onDismiss = onDismiss,
        onEpisodeSelected = onEpisodeSelected,
        onSettingsAction = onSettingsAction,
        onOverlayEvent = onOverlayEvent,
        content = content,
    )
}
