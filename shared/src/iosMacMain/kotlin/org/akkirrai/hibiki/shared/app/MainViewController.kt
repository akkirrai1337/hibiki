package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import org.akkirrai.hibiki.shared.catalog.IosMultiSourceAnimeCatalogRepository
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.home.CatalogBackedHomeDataRepository
import org.akkirrai.hibiki.shared.library.IosLibraryRepository
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.AppNavigationBarMode
import org.akkirrai.hibiki.shared.layout.AppScreenEdgePolicy
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.shared.profile.IosAvatarPicker
import org.akkirrai.hibiki.shared.profile.IosLocalProfileRepository
import org.akkirrai.hibiki.shared.profile.IosWatchStateRepository
import org.akkirrai.hibiki.shared.settings.IosAppSettingsStore
import org.akkirrai.hibiki.shared.settings.requestIosNotificationPermission
import org.akkirrai.hibiki.shared.source.IosSourceRegistry
import org.akkirrai.hibiki.shared.source.IosSourceSelectionRepository
import org.akkirrai.hibiki.shared.player.IosAnimeWatchRepository
import org.akkirrai.hibiki.shared.platform.IosBackBridge
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(systemLanguage: String): UIViewController {
    val avatarPicker = IosAvatarPicker()
    lateinit var hostController: UIViewController
    hostController = ComposeUIViewController(configure = { parallelRendering = false }) {
        val sourceSelectionRepository = remember { IosSourceSelectionRepository() }
        val initialSourceId = remember { sourceSelectionRepository.loadSelectedSourceId() }
        val repository = remember(systemLanguage, initialSourceId) {
            IosMultiSourceAnimeCatalogRepository(
                preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
                initialSourceId = initialSourceId,
            )
        }
        val watchRepository = remember(systemLanguage) {
            IosAnimeWatchRepository(preferEnglish = !systemLanguage.lowercase().startsWith("ru"))
        }
        val libraryRepository = remember { IosLibraryRepository() }
        val watchStateRepository = remember { IosWatchStateRepository() }
        val homeRepository = remember(repository, libraryRepository) {
            CatalogBackedHomeDataRepository(repository, libraryRepository, watchStateRepository)
        }
        val profileRepository = remember(libraryRepository) { IosLocalProfileRepository(libraryRepository) }
        val settingsStore = remember { IosAppSettingsStore() }
        val notificationPermissionState = remember {
            androidx.compose.runtime.mutableStateOf(settingsStore.load().notificationPermissionState)
        }
        val selectedSourceId = remember { androidx.compose.runtime.mutableStateOf(initialSourceId) }
        val requestNotificationPermission = {
            requestIosNotificationPermission { state ->
                notificationPermissionState.value = state
                settingsStore.save(settingsStore.load().copy(notificationPermissionState = state))
            }
        }
        DisposableEffect(repository) { onDispose { repository.close() } }
        DisposableEffect(watchRepository) { onDispose { watchRepository.close() } }
        MaterialTheme(colorScheme = HibikiLightColorScheme, typography = HibikiTypography) {
            val density = LocalDensity.current
            val safeDrawingInsets = AppLayoutEnvironment(
                isProvided = true,
                topSystemInset = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() },
                bottomSystemInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() },
                navigationBarMode = AppNavigationBarMode.Inset,
                edgePolicy = AppScreenEdgePolicy.ContentSafe,
            )
            CompositionLocalProvider(LocalAppLayoutEnvironment provides safeDrawingInsets) {
                Surface {
                    HibikiApp(
                    repository = repository,
                    watchRepository = watchRepository,
                    onPlaybackReady = { playback, context ->
                        if (!USE_EMBEDDED_IOS_PLAYER) {
                            presentPlayback(hostController, playback, context)
                        }
                    },
                    onPlaybackSelectionChanged = { selection ->
                        watchStateRepository.savePlaybackSelection(selection)
                    },
                    loadPlaybackSelection = watchStateRepository::loadPlaybackSelection,
                    onWatchSourceSelected = { titleId, source ->
                        watchStateRepository.savePlaybackSelection(
                            org.akkirrai.hibiki.shared.model.PlaybackSelection(
                                titleId = titleId,
                                sourceId = source.sourceId,
                                sourceTitle = source.title,
                                quality = source.qualityLabel,
                                playerName = null,
                            ),
                        )
                    },
                    playbackHost = if (USE_EMBEDDED_IOS_PLAYER) {
                        { playback, context, navigationState, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                            IosEmbeddedPlaybackHost(
                                playback = playback,
                                context = context,
                                navigationState = navigationState,
                                onBack = onBack,
                                onEpisodeSelected = onEpisodeSelected,
                                settingsStore = settingsStore,
                                onSettingsAction = onSettingsAction,
                                progressRepository = watchStateRepository,
                                onOverlayEvent = onOverlayEvent,
                            )
                        }
                    } else {
                        null
                    },
                    homeRepository = homeRepository,
                    libraryRepository = libraryRepository,
                    profileRepository = profileRepository,
                    settingsStore = settingsStore,
                    progressRepository = watchStateRepository,
                    systemLanguage = systemLanguage,
                    enableOnboarding = true,
                    onboardingNotificationPermissionState = notificationPermissionState.value,
                    onRequestOnboardingNotificationPermission = requestNotificationPermission,
                    onConfigureNotifications = requestNotificationPermission,
                    notificationsAvailable = true,
                    appVersionName = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "dev",
                    onProfileAvatarEdit = { onPicked -> avatarPicker.present(hostController, onPicked) },
                    profileAvatarEditAvailable = true,
                    onGitHubClick = { UIApplication.sharedApplication.openURL(NSURL(string = HIBIKI_GITHUB_URL)) },
                    onOpenUrl = { url -> UIApplication.sharedApplication.openURL(NSURL(string = url)) },
                    sources = IosSourceRegistry.sources,
                    selectedSourceId = selectedSourceId.value,
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
    }
    IosBackBridge.install(hostController)
    return hostController
}

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"
private const val USE_EMBEDDED_IOS_PLAYER = true

/** Temporary fallback retained until the AVPlayerLayer host is smoke-tested on a device. */
private fun presentPlayback(
    hostController: UIViewController,
    playback: PlaybackStream,
    context: PlaybackContext,
) {
    val headers = buildMap {
        put("User-Agent", "Hibiki/0.1 iOS")
        putAll(playback.headers)
    }
    val asset = AVURLAsset(
        uRL = NSURL(string = playback.streamUrl),
        options = mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to headers),
    )
    val player = AVPlayer(playerItem = AVPlayerItem(asset = asset))
    AVPlayerViewController().also { controller ->
        controller.player = player
        hostController.presentViewController(controller, animated = true, completion = null)
    }
}
