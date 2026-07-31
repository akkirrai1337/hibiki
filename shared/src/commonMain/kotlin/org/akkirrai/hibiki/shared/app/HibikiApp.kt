package org.akkirrai.hibiki.shared.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.prototype.PrototypeLocalProfileDataRepository
import org.akkirrai.hibiki.shared.prototype.PrototypeLibraryRepository
import org.akkirrai.hibiki.shared.prototype.HibikiAppShell
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.DiscordRpcController
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.home.HomeDataRepository
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.shared.details.OfflineTitleMetadataRepository

/** Canonical shared application entry point for platform hosts. */
@Composable
fun HibikiApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository = PrototypeLibraryRepository,
    profileRepository: LocalProfileDataRepository = PrototypeLocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    progressRepository: PlaybackProgressRepository? = null,
    episodeDownloadRepository: EpisodeDownloadRepository? = null,
    offlineWatchDataRepository: OfflineWatchDataRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
    resumeFrameContent: (@Composable (String, Modifier) -> Unit)? = null,
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    onRequestOnboardingNotificationPermission: () -> Unit = {},
    onConfigureNotifications: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onProfileAvatarEdit: (((String) -> Unit) -> Unit) = {},
    profileAvatarEditAvailable: Boolean = false,
    onGitHubClick: () -> Unit = {},
    discordRpcController: DiscordRpcController? = null,
    onDiscordBrowserSignIn: (((String) -> Unit) -> Unit) = {},
    sources: List<AppSourceDescriptor> = emptyList(),
    selectedSourceId: String? = null,
    onSourceSelected: (String) -> Unit = {},
    watchRepository: WatchDataRepository? = null,
    onPlaybackReady: (PlaybackStream, PlaybackContext) -> Unit = { _, _ -> },
    playbackHost: (@Composable (PlaybackStream, PlaybackContext, () -> Unit, (WatchEpisode) -> Unit, (PlaybackSettingsAction) -> Unit, (AppNavigationEvent) -> Unit) -> Unit)? = null,
    showSettingsBackButton: Boolean = false,
    includeNavigationBarPadding: Boolean = true,
    applyStatusBarPadding: Boolean = false,
) {
    HibikiAppShell(
        modifier = modifier,
        repository = repository,
        homeRepository = homeRepository,
        libraryRepository = libraryRepository,
        profileRepository = profileRepository,
        settingsStore = settingsStore,
        progressRepository = progressRepository,
        episodeDownloadRepository = episodeDownloadRepository,
        offlineWatchDataRepository = offlineWatchDataRepository,
        offlineTitleMetadataRepository = offlineTitleMetadataRepository,
        resumeFrameContent = resumeFrameContent,
        systemLanguage = systemLanguage,
        appVersionName = appVersionName,
        enableOnboarding = enableOnboarding,
        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
        onRequestOnboardingNotificationPermission = onRequestOnboardingNotificationPermission,
        onConfigureNotifications = onConfigureNotifications,
        onCheckForUpdates = onCheckForUpdates,
        onExportLogs = onExportLogs,
        onOpenUrl = onOpenUrl,
        onProfileAvatarEdit = onProfileAvatarEdit,
        profileAvatarEditAvailable = profileAvatarEditAvailable,
        onGitHubClick = onGitHubClick,
        discordRpcController = discordRpcController,
        onDiscordBrowserSignIn = onDiscordBrowserSignIn,
        sources = sources,
        selectedSourceId = selectedSourceId,
        onSourceSelected = onSourceSelected,
        watchRepository = watchRepository,
        onPlaybackReady = onPlaybackReady,
        playbackHost = playbackHost,
        showSettingsBackButton = showSettingsBackButton,
        includeNavigationBarPadding = includeNavigationBarPadding,
        applyStatusBarPadding = applyStatusBarPadding,
    )
}
