package org.akkirrai.hibiki.shared.app.shell.overlay

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.app.shell.player.HibikiPlaybackOverlayLayer
import org.akkirrai.hibiki.shared.app.shell.onboarding.HibikiOnboardingOverlayLayer
import org.akkirrai.hibiki.shared.app.shell.settings.HibikiDiscordAuthOverlayLayer
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.AppPlaybackHost
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

@Composable
internal fun HibikiAppShellOverlayLayer(
    playbackHost: AppPlaybackHost?,
    currentRoute: AppRoute,
    activePlaybackRoute: PlaybackRoute?,
    pendingPlaybackContext: PlaybackContext?,
    navigationState: AppNavigationState,
    playbackLoading: Boolean,
    playbackError: String?,
    onRetryPlayback: () -> Unit,
    onDismissPlayback: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onPlaybackSettingsAction: (PlaybackSettingsAction) -> Unit,
    onPlaybackOverlayEvent: (AppNavigationEvent) -> Unit,
    enableOnboarding: Boolean,
    onboardingCompleted: Boolean,
    sources: List<AppSourceDescriptor>,
    onboardingSourceId: String?,
    systemLanguage: String,
    onboardingNotificationPermissionState: NotificationPermissionState,
    onRequestOnboardingNotificationPermission: () -> Unit,
    onOnboardingComplete: (String) -> Unit,
    discordAuthDialogOpen: Boolean,
    discordRpcController: DiscordRpcController?,
    discordRpcState: DiscordRpcUiState,
    pendingDiscordToken: String?,
    onPendingDiscordTokenChange: (String?) -> Unit,
    onDiscordBrowserSignIn: (((String) -> Unit) -> Unit),
    onCloseDiscordAuth: () -> Unit,
) {
    HibikiPlaybackOverlayLayer(
        playbackHost = playbackHost,
        currentRoute = currentRoute,
        activePlaybackRoute = activePlaybackRoute,
        pendingPlaybackContext = pendingPlaybackContext,
        navigationState = navigationState,
        playbackLoading = playbackLoading,
        playbackError = playbackError,
        onRetry = onRetryPlayback,
        onDismiss = onDismissPlayback,
        onEpisodeSelected = onEpisodeSelected,
        onSettingsAction = onPlaybackSettingsAction,
        onOverlayEvent = onPlaybackOverlayEvent,
    )
    HibikiOnboardingOverlayLayer(
        enabled = enableOnboarding,
        completed = onboardingCompleted,
        sources = sources,
        initialSourceId = onboardingSourceId,
        systemLanguage = systemLanguage,
        notificationPermissionState = onboardingNotificationPermissionState,
        onRequestNotificationPermission = onRequestOnboardingNotificationPermission,
        onComplete = onOnboardingComplete,
    )
    HibikiDiscordAuthOverlayLayer(
        isOpen = discordAuthDialogOpen,
        controller = discordRpcController,
        state = discordRpcState,
        pendingToken = pendingDiscordToken,
        onPendingTokenChange = onPendingDiscordTokenChange,
        onBrowserSignIn = onDiscordBrowserSignIn,
        onClose = onCloseDiscordAuth,
    )
}
