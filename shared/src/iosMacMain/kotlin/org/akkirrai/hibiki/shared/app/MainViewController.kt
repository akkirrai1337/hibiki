package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.hibiki.shared.catalog.ExternalSourceCatalogRepository
import org.akkirrai.hibiki.shared.catalog.IosMultiSourceAnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.TransitionalAnimeCatalogRepository
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.home.data.CatalogBackedHomeDataRepository
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
import org.akkirrai.hibiki.shared.source.AppSourceConfigLabels
import org.akkirrai.hibiki.shared.source.AppSourceConfigScreen
import org.akkirrai.hibiki.shared.source.IosExternalSourceConfigStore
import org.akkirrai.hibiki.shared.source.IosSourceRegistry
import org.akkirrai.hibiki.shared.source.ExternalAnimeStatusLabels
import org.akkirrai.hibiki.shared.source.createIosExternalSourceRepositoryPlatform
import org.akkirrai.hibiki.shared.source.createIosExternalSourceRuntimeFactory
import org.akkirrai.hibiki.shared.source.ExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.IosSourceSelectionRepository
import org.akkirrai.hibiki.shared.source.mergeAppSourceDescriptors
import org.akkirrai.hibiki.shared.source.toAppSourceDescriptors
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.player.IosAnimeWatchRepository
import org.akkirrai.hibiki.shared.player.RoutingWatchDataRepository
import org.akkirrai.hibiki.shared.player.SharedAnimeWatchRepository
import org.akkirrai.hibiki.shared.platform.IosBackBridge
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(systemLanguage: String): UIViewController {
    val avatarPicker = IosAvatarPicker()
    lateinit var hostController: UIViewController
    hostController = ComposeUIViewController(configure = { parallelRendering = false }) {
        val externalSourcePlatform = remember { createIosExternalSourceRepositoryPlatform() }
        val externalRuntimeHttpClient = remember {
            HttpClient(Darwin) {
                followRedirects = false
            }
        }
        val externalConfigStore = remember { IosExternalSourceConfigStore() }
        val externalRuntimeCoordinator = remember(externalSourcePlatform, externalRuntimeHttpClient) {
            ExternalSourceRuntimeCoordinator(
                platform = externalSourcePlatform,
                catalogCapabilities = { CatalogCapabilities.FULL },
                runtimeFactory = createIosExternalSourceRuntimeFactory(externalRuntimeHttpClient),
                sourceContextFactory = { sourceId ->
                    DefaultSourceContext(
                        httpClient = externalRuntimeHttpClient,
                        preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
                        config = externalConfigStore.load(sourceId),
                    )
                },
                reservedSourceIds = IosSourceRegistry.sources.mapTo(linkedSetOf()) { SourceId(it.id) },
            )
        }
        val externalRefreshScope = rememberCoroutineScope()
        val externalRepositoryController = remember(externalRuntimeCoordinator) {
            ExternalSourceRepositoryController(
                actions = externalRuntimeCoordinator,
                scope = externalRefreshScope,
            )
        }
        DisposableEffect(externalSourcePlatform) {
            val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = null,
            ) {
                externalRefreshScope.launch {
                    externalRepositoryController.refreshRepositories()
                }
            }
            onDispose {
                NSNotificationCenter.defaultCenter.removeObserver(activeObserver)
            }
        }
        DisposableEffect(externalSourcePlatform) {
            onDispose {
                externalRepositoryController.close()
                externalRuntimeCoordinator.close()
                externalRuntimeHttpClient.close()
            }
        }
        val sourceSelectionRepository = remember { IosSourceSelectionRepository() }
        val initialSourceId = remember { sourceSelectionRepository.loadSelectedSourceId() }
        val builtInRepository = remember(systemLanguage, initialSourceId) {
            IosMultiSourceAnimeCatalogRepository(
                preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
                initialSourceId = initialSourceId,
            )
        }
        val externalCatalogRepository = remember(externalRuntimeCoordinator, externalRuntimeHttpClient) {
            ExternalSourceCatalogRepository(
                registryProvider = { externalRuntimeCoordinator.snapshot.value.registry },
                contextProvider = { sourceId ->
                    DefaultSourceContext(
                        httpClient = externalRuntimeHttpClient,
                        preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
                        config = externalConfigStore.load(sourceId),
                    )
                },
                statusLabels = ExternalAnimeStatusLabels(
                    unknown = appText(AppTextKey.Unknown),
                    ongoing = appText(AppTextKey.Ongoing),
                    released = appText(AppTextKey.Released),
                    announcement = appText(AppTextKey.Announcement),
                ),
            )
        }
        val repository = remember(builtInRepository, externalCatalogRepository) {
            TransitionalAnimeCatalogRepository(
                builtIn = builtInRepository,
                external = externalCatalogRepository,
            )
        }
        val externalRegistry = externalRuntimeCoordinator.snapshot.collectAsState().value.registry
        LaunchedEffect(repository, initialSourceId, externalRegistry) {
            initialSourceId?.let(repository::selectSource)
        }
        val sources = remember(externalRegistry) {
            mergeAppSourceDescriptors(
                builtIn = IosSourceRegistry.sources,
                external = externalRegistry?.toAppSourceDescriptors().orEmpty(),
            )
        }
        val watchRepository = remember(systemLanguage) {
            IosAnimeWatchRepository(preferEnglish = !systemLanguage.lowercase().startsWith("ru"))
        }
        val externalWatchRepository = remember(externalRuntimeCoordinator, externalRegistry) {
            SharedAnimeWatchRepository(
                client = HttpClient(Darwin) {
                    installBeakoKitHttpDefaults(
                        BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 iOS external-source"),
                    )
                },
                sourceHttpClient = externalRuntimeHttpClient,
                preferEnglish = !systemLanguage.lowercase().startsWith("ru"),
                sourceConfigProvider = externalConfigStore::load,
                externalSourceFactory = { sourceId, sourceContext ->
                    externalRuntimeCoordinator.snapshot.value.registry?.create(sourceId, sourceContext)
                },
            )
        }
        val routedWatchRepository = remember(watchRepository, externalWatchRepository, externalRegistry) {
            RoutingWatchDataRepository(
                builtIn = watchRepository,
                external = externalWatchRepository,
                isExternalSource = { sourceId ->
                    externalRuntimeCoordinator.snapshot.value.registry?.sources
                        ?.any { it.id == sourceId }
                        ?: false
                },
            )
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
        DisposableEffect(builtInRepository) { onDispose { builtInRepository.close() } }
        DisposableEffect(watchRepository) { onDispose { watchRepository.close() } }
        DisposableEffect(externalWatchRepository) { onDispose { externalWatchRepository.close() } }
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
                    org.akkirrai.hibiki.shared.app.screen.HibikiApp(
                    repository = repository,
                    watchRepository = routedWatchRepository,
                    playbackCallbacks = org.akkirrai.hibiki.shared.player.AppPlaybackPlatformCallbacks(
                        onWatchSourceSelected = { titleId, source ->
                            watchStateRepository.savePlaybackSelection(
                                org.akkirrai.hibiki.shared.player.model.PlaybackSelection(
                                    titleId = titleId,
                                    sourceId = source.sourceId,
                                    sourceTitle = source.title,
                                    quality = source.qualityLabel,
                                    playerName = null,
                                ),
                            )
                        },
                        onPlaybackReady = { playback, context ->
                            if (!USE_EMBEDDED_IOS_PLAYER) {
                                presentPlayback(hostController, playback, context)
                            }
                        },
                        onPlaybackSelectionChanged = watchStateRepository::savePlaybackSelection,
                        loadPlaybackSelection = watchStateRepository::loadPlaybackSelection,
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
                    ),
                    homeRepository = homeRepository,
                    libraryRepository = libraryRepository,
                    profileRepository = profileRepository,
                    settingsStore = settingsStore,
                    progressRepository = watchStateRepository,
                    systemLanguage = systemLanguage,
                    enableOnboarding = true,
                    onboardingNotificationPermissionState = notificationPermissionState.value,
                    appVersionName = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "dev",
                    platformCallbacks = org.akkirrai.hibiki.shared.app.screen.AppPlatformCallbacks(
                        onRequestOnboardingNotificationPermission = requestNotificationPermission,
                        onConfigureNotifications = requestNotificationPermission,
                        notificationsAvailable = true,
                        onProfileAvatarEdit = { onPicked -> avatarPicker.present(hostController, onPicked) },
                        profileAvatarEditAvailable = true,
                        onGitHubClick = { UIApplication.sharedApplication.openURL(NSURL(string = HIBIKI_GITHUB_URL)) },
                        onOpenUrl = { url -> UIApplication.sharedApplication.openURL(NSURL(string = url)) },
                    ),
                    sourceCallbacks = org.akkirrai.hibiki.shared.source.AppSourcePlatformCallbacks(
                        externalSourceRepositoryController = externalRepositoryController,
                        sources = sources,
                        selectedSourceId = selectedSourceId.value,
                        sourceConfigContent = { source, onSaved, onCancel ->
                        val sourceId = SourceId(source.id)
                        val draft = externalConfigStore.loadDraft(sourceId)
                        AppSourceConfigScreen(
                            schema = source.configSchema,
                            initialValues = draft.values,
                            initialSecrets = draft.secrets,
                            labels = AppSourceConfigLabels(
                                fieldLabel = { field -> field.titleKey },
                                saveLabel = appText(AppTextKey.Apply),
                                cancelLabel = appText(AppTextKey.Cancel),
                            ),
                            onSave = { values, secrets ->
                                source.configSchema.fields.forEach { field ->
                                    if (field.kind == org.akkirrai.beakokit.api.SourceConfigValueKind.SECRET) {
                                        secrets[field.key]?.let { value ->
                                            if (value.isBlank()) {
                                                externalConfigStore.clearSecret(sourceId, field.key)
                                            } else {
                                                externalConfigStore.saveSecret(sourceId, field.key, value)
                                            }
                                        }
                                    } else {
                                        values[field.key]?.let { value ->
                                            val normalizedValue = value.trim()
                                            if (normalizedValue.isEmpty()) {
                                                externalConfigStore.clearValue(sourceId, field.key)
                                            } else {
                                                externalConfigStore.saveValue(sourceId, field.key, normalizedValue)
                                            }
                                        }
                                    }
                                }
                                externalWatchRepository.invalidateSource(sourceId)
                                onSaved()
                            },
                            onCancel = onCancel,
                        )
                        },
                        onSourceSelected = { sourceId ->
                            repository.selectSource(sourceId)
                            sourceSelectionRepository.saveSelectedSourceId(sourceId)
                            selectedSourceId.value = sourceId
                        },
                    ),
                    layoutOptions = org.akkirrai.hibiki.shared.layout.AppLayoutOptions(
                        includeNavigationBarPadding = true,
                    ),
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
