package org.akkirrai.hibiki.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.catalog.EmptyAnimeCatalogRepository
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.app.shell.HibikiAppShell
import org.akkirrai.hibiki.app.settings.AppSettingsStore
import org.akkirrai.hibiki.app.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.core.source.LocalAppSourceConfigContent
import org.akkirrai.hibiki.core.source.AppSourcePlatformCallbacks
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.player.WatchDataRepository
import org.akkirrai.hibiki.player.AppPlaybackPlatformCallbacks
import org.akkirrai.hibiki.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.player.OfflineWatchDataRepository
import org.akkirrai.hibiki.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.layout.AppLayoutOptions

/** Canonical shared application entry point for platform hosts. */
@Composable
fun HibikiApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = EmptyAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    progressRepository: PlaybackProgressRepository? = null,
    episodeDownloadRepository: EpisodeDownloadRepository? = null,
    offlineWatchDataRepository: OfflineWatchDataRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    platformCallbacks: AppPlatformCallbacks = AppPlatformCallbacks(),
    sourceCallbacks: AppSourcePlatformCallbacks = AppSourcePlatformCallbacks(),
    watchRepository: WatchDataRepository? = null,
    playbackCallbacks: AppPlaybackPlatformCallbacks = AppPlaybackPlatformCallbacks(),
    layoutOptions: AppLayoutOptions = AppLayoutOptions(),
    catalogRefreshKey: Any? = null,
    catalogReady: Boolean = true,
) {
    CompositionLocalProvider(LocalAppSourceConfigContent provides sourceCallbacks.sourceConfigContent) {
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
        systemLanguage = systemLanguage,
        appVersionName = appVersionName,
        enableOnboarding = enableOnboarding,
        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
        platformCallbacks = platformCallbacks,
        sourceCallbacks = sourceCallbacks,
        watchRepository = watchRepository,
        playbackCallbacks = playbackCallbacks,
        layoutOptions = layoutOptions,
        catalogRefreshKey = catalogRefreshKey,
        catalogReady = catalogReady,
        )
    }
}
