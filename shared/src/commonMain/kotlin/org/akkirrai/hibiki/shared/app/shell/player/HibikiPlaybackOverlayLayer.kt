package org.akkirrai.hibiki.shared.app.shell.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.player.HibikiPlaybackOverlay
import org.akkirrai.hibiki.shared.player.AppPlaybackHost
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.shouldShowPlaybackHost

@Composable
internal fun HibikiPlaybackOverlayLayer(
    playbackHost: AppPlaybackHost?,
    currentRoute: AppRoute,
    activePlaybackRoute: PlaybackRoute?,
    pendingPlaybackContext: PlaybackContext?,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    if (playbackHost != null && shouldShowPlaybackHost(
            currentRoute = currentRoute,
            hasPlayback = activePlaybackRoute != null,
            hasPendingContext = pendingPlaybackContext != null,
        )
    ) {
        HibikiPlaybackOverlay(
            route = activePlaybackRoute,
            pendingContext = pendingPlaybackContext,
            navigationState = navigationState,
            playbackLoading = playbackLoading,
            playbackError = playbackError,
            onRetry = onRetry,
            onDismiss = onDismiss,
            onEpisodeSelected = onEpisodeSelected,
            onSettingsAction = onSettingsAction,
            onOverlayEvent = onOverlayEvent,
            content = playbackHost,
        )
    }
}
