package org.akkirrai.hibiki.shared.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.ExperimentalComposeUiApi
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.catalog.IosMultiSourceAnimeCatalogRepository
import org.akkirrai.hibiki.shared.home.CatalogBackedHomeDataRepository
import org.akkirrai.hibiki.shared.library.IosLibraryRepository
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import org.akkirrai.hibiki.shared.settings.IosAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.requestIosNotificationPermission
import org.akkirrai.hibiki.shared.profile.IosLocalProfileRepository
import org.akkirrai.hibiki.shared.profile.IosAvatarPicker
import org.akkirrai.hibiki.shared.profile.IosWatchStateRepository
import org.akkirrai.hibiki.shared.source.IosSourceRegistry
import org.akkirrai.hibiki.shared.source.IosSourceSelectionRepository
import org.akkirrai.hibiki.shared.player.IosAnimeWatchRepository
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.PlaybackContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVKit.AVPlayerViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(systemLanguage: String): UIViewController {
    val avatarPicker = IosAvatarPicker()
    lateinit var hostController: UIViewController
    hostController = ComposeUIViewController(
        configure = { parallelRendering = false },
    ) {
    val sourceSelectionRepository = remember { IosSourceSelectionRepository() }
    val initialSourceId = remember { sourceSelectionRepository.loadSelectedSourceId() }
    val repository = remember(systemLanguage, initialSourceId) {
        IosMultiSourceAnimeCatalogRepository(
            preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
            initialSourceId = initialSourceId,
        )
    }
    val watchRepository = remember(systemLanguage) {
        IosAnimeWatchRepository(
            preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
        )
    }
    val libraryRepository = remember { IosLibraryRepository() }
    val watchStateRepository = remember { IosWatchStateRepository() }
    val homeRepository = remember(repository, libraryRepository) {
        CatalogBackedHomeDataRepository(repository, libraryRepository, watchStateRepository)
    }
    val profileRepository = remember(libraryRepository) {
        IosLocalProfileRepository(libraryRepository)
    }
    val settingsStore = remember { IosAppSettingsStore() }
    var notificationPermissionState = remember {
        androidx.compose.runtime.mutableStateOf(settingsStore.load().notificationPermissionState)
    }
    var selectedSourceId = remember {
        androidx.compose.runtime.mutableStateOf(initialSourceId)
    }
    val requestNotificationPermission = {
        requestIosNotificationPermission { state ->
            notificationPermissionState.value = state
            settingsStore.save(settingsStore.load().copy(notificationPermissionState = state))
        }
    }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    DisposableEffect(watchRepository) {
        onDispose { watchRepository.close() }
    }
    MaterialTheme(
        colorScheme = HibikiLightColorScheme,
        typography = HibikiTypography,
    ) {
        Surface {
            HibikiApp(
                repository = repository,
                watchRepository = watchRepository,
                onPlaybackReady = { playback, context ->
                    presentPlayback(hostController, playback, context, watchStateRepository)
                },
                playbackHost = if (USE_EMBEDDED_IOS_PLAYER) {
                    { playback, context, onBack ->
                        IosEmbeddedPlaybackHost(playback, context, onBack)
                    }
                } else {
                    null
                },
                homeRepository = homeRepository,
                libraryRepository = libraryRepository,
                profileRepository = profileRepository,
                settingsStore = settingsStore,
                systemLanguage = systemLanguage,
                enableOnboarding = true,
                onboardingNotificationPermissionState = notificationPermissionState.value,
                onRequestOnboardingNotificationPermission = requestNotificationPermission,
                onConfigureNotifications = requestNotificationPermission,
                appVersionName = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
                    ?: "dev",
                onProfileAvatarEdit = { onPicked ->
                    avatarPicker.present(hostController, onPicked)
                },
                onGitHubClick = {
                    UIApplication.sharedApplication.openURL(
                        NSURL(string = HIBIKI_GITHUB_URL),
                    )
                },
                sources = IosSourceRegistry.sources,
                selectedSourceId = selectedSourceId.value,
                showSettingsBackButton = true,
                includeNavigationBarPadding = true,
                onSourceSelected = { sourceId ->
                    repository.selectSource(sourceId)
                    sourceSelectionRepository.saveSelectedSourceId(sourceId)
                    selectedSourceId.value = sourceId
                },
            )
        }
    }
    }
    return hostController
}

private fun presentPlayback(
    hostController: UIViewController,
    playback: PlaybackStream,
    context: PlaybackContext,
    watchStateRepository: IosWatchStateRepository,
) {
    val url = NSURL(string = playback.streamUrl)
    val playerController = AVPlayerViewController()
    val playbackHeaders = buildMap {
        put("User-Agent", "Hibiki/0.1 iOS")
        putAll(playback.headers)
    }
    val assetOptions = if (playbackHeaders.isEmpty()) {
        null
    } else {
        mapOf<Any?, Any?>(
            "AVURLAssetHTTPHeaderFieldsKey" to playbackHeaders,
        )
    }
    val asset = AVURLAsset(uRL = url, options = assetOptions)
    val playerItem = AVPlayerItem(asset = asset)
    val player = AVPlayer(playerItem = playerItem)
    playerController.player = player
    hostController.presentViewController(playerController, animated = true, completion = null)
}

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
private const val USE_EMBEDDED_IOS_PLAYER = true
