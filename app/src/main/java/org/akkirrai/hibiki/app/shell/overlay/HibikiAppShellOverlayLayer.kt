package org.akkirrai.hibiki.app.shell.overlay

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.app.shell.settings.HibikiDiscordAuthOverlayLayer
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.activeOverlay
import org.akkirrai.hibiki.app.navigation.reduceOverlayVisibilityChange
import org.akkirrai.hibiki.onboarding.AppOnboardingScreen
import org.akkirrai.hibiki.player.AppPlaybackHost
import org.akkirrai.hibiki.player.HibikiPlaybackOverlay
import org.akkirrai.hibiki.player.PlaybackSettingsAction
import org.akkirrai.hibiki.player.WatchDataRepository
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackRoute
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.shouldShowPlaybackHost
import org.akkirrai.hibiki.app.settings.DiscordRpcController
import org.akkirrai.hibiki.app.settings.DiscordRpcUiState
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

internal class HibikiOverlayActions(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val libraryFilterOverlay: AppOverlay,
) {
    fun setLibraryFilterVisible(visible: Boolean) {
        if (!visible && navigationState().activeOverlay != libraryFilterOverlay) return
        setNavigationState(
            navigationState().reduceOverlayVisibilityChange(libraryFilterOverlay, visible),
        )
    }

}

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
    onEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit,
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

@Composable
internal fun HibikiOnboardingOverlayLayer(
    enabled: Boolean,
    completed: Boolean,
    sources: List<AppSourceDescriptor>,
    initialSourceId: String?,
    systemLanguage: String,
    notificationPermissionState: NotificationPermissionState,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (String) -> Unit,
) {
    if (enabled && !completed) {
        AppOnboardingScreen(
            sources = sources,
            initialSourceId = initialSourceId,
            systemLanguage = systemLanguage,
            notificationPermissionState = notificationPermissionState,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onComplete = onComplete,
        )
    }
}

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
    onEpisodeSelected: (WatchEpisode, PlaybackContext) -> Unit,
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
