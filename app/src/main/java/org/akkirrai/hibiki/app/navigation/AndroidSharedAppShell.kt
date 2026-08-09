package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.AndroidExternalSourceConfigStore
import org.akkirrai.hibiki.feature.player.AndroidPlayerWindowController
import org.akkirrai.hibiki.feature.player.AndroidEpisodeDownloadRepository
import org.akkirrai.hibiki.feature.details.AndroidOfflineTitleMetadataRepository
import org.akkirrai.hibiki.feature.settings.AndroidDiscordRpcController
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.R
import android.widget.Toast
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.shared.app.screen.HibikiApp as SharedHibikiApp
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceConfigLabels
import org.akkirrai.hibiki.shared.source.AppSourceConfigScreen
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.source.ExternalAnimeStatusLabels
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.shared.source.LocalExternalSourceRuntimeCoordinator
import org.akkirrai.hibiki.shared.source.mergeAppSourceDescriptors
import org.akkirrai.hibiki.shared.source.toAppSourceDescriptors
import org.akkirrai.beakokit.api.SourceConfigValueKind
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.catalog.ExternalSourceCatalogRepository
import org.akkirrai.hibiki.shared.catalog.TransitionalAnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.EmptyAnimeCatalogRepository
import org.akkirrai.hibiki.shared.player.RoutingWatchDataRepository
import org.akkirrai.hibiki.shared.player.SharedAnimeWatchRepository
import org.akkirrai.hibiki.shared.player.AppPlaybackPlatformCallbacks
import org.akkirrai.hibiki.shared.app.screen.AppPlatformCallbacks
import org.akkirrai.hibiki.shared.source.AppSourcePlatformCallbacks

/** Android adapter for the shared shell with external sources kept behind the built-in path. */
@Composable
internal fun AndroidSharedAppShell(
    activity: Activity,
    onCheckForUpdates: () -> Unit,
    onConfigureNotifications: () -> Unit,
    enableOnboarding: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val externalSourceConfigStore = remember(context) { AndroidExternalSourceConfigStore(context) }
    val uriHandler = LocalUriHandler.current
    val activityLaunchers = rememberAndroidSharedAppActivityLaunchers(context)
    val dependencies = remember(context) { context.hibikiDependencies() }
    val settingsStore = remember(dependencies) { dependencies.appSettingsStore() }
    val discordRpcController = remember(context) { AndroidDiscordRpcController(context) }
    val externalCoordinator = LocalExternalSourceRuntimeCoordinator.current
    val externalSnapshot = externalCoordinator?.snapshot?.collectAsState()?.value
    val externalRepositoryControllerScope = rememberCoroutineScope()
    val externalRepositoryController = remember(externalCoordinator) {
        externalCoordinator?.let {
            ExternalSourceRepositoryController(
                actions = it,
                scope = externalRepositoryControllerScope,
                operationContext = Dispatchers.IO,
            )
        }
    }
    DisposableEffect(externalRepositoryController) {
        onDispose { externalRepositoryController?.close() }
    }
    // External source search/details must keep redirects inside the manifest's host policy.
    val externalHttpClient = remember {
        HttpClient(OkHttp) {
            installBeakoKitHttpDefaults(
                BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android external-source"),
            )
            followRedirects = false
        }
    }
    DisposableEffect(externalHttpClient) {
        onDispose { externalHttpClient.close() }
    }
    val externalStatusLabels = ExternalAnimeStatusLabels(
        unknown = appText(AppTextKey.Unknown),
        ongoing = appText(AppTextKey.Ongoing),
        released = appText(AppTextKey.Released),
        announcement = appText(AppTextKey.Announcement),
    )
    val externalCatalogRepository = remember(externalCoordinator, externalHttpClient, externalStatusLabels) {
        ExternalSourceCatalogRepository(
            registryProvider = { externalCoordinator?.snapshot?.value?.registry },
            registryAwaiter = { externalCoordinator?.awaitRegistry() },
            contextProvider = { sourceId ->
                DefaultSourceContext(
                    httpClient = externalHttpClient,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
                    config = externalSourceConfigStore.load(sourceId),
                    logger = SourceLogger { level, message, throwable ->
                        val tag = "BeakoKit/${sourceId.value}"
                        when (level) {
                            SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                            SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                            SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
                        }
                    },
                )
            },
            statusLabels = externalStatusLabels,
        )
    }
    val builtInCatalogRepository = EmptyAnimeCatalogRepository
    val catalogRepository = remember(builtInCatalogRepository, externalCatalogRepository) {
        TransitionalAnimeCatalogRepository(builtInCatalogRepository, externalCatalogRepository)
    }
    val homeRepository = remember(dependencies) { dependencies.homeRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val profileRepository = remember(dependencies) { dependencies.localProfileRepository() }
    val watchRepository = remember(dependencies) { dependencies.animeWatchRepository() }
    val externalWatchRepository = remember(externalCoordinator, externalSnapshot?.registry) {
        externalCoordinator?.let { coordinator ->
            SharedAnimeWatchRepository(
                client = HttpClient(OkHttp) {
                    installBeakoKitHttpDefaults(
                        BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android external-playback"),
                    )
                },
                sourceHttpClient = externalHttpClient,
                externalSourceFactory = { sourceId, sourceContext ->
                    coordinator.snapshot.value.registry?.create(sourceId, sourceContext)
                },
                sourceConfigProvider = externalSourceConfigStore::load,
            )
        }
    }
    DisposableEffect(externalWatchRepository) {
        onDispose { externalWatchRepository?.close() }
    }
    val routedWatchRepository = remember(
        watchRepository,
        externalWatchRepository,
        externalCoordinator,
        externalSnapshot?.registry,
    ) {
        externalWatchRepository?.let { externalRepository ->
            RoutingWatchDataRepository(
                builtIn = watchRepository,
                external = externalRepository,
                isExternalSource = { sourceId ->
                    externalCoordinator?.snapshot?.value?.registry?.sources
                        ?.any { it.id == sourceId }
                        ?: false
                },
            )
        } ?: watchRepository
    }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val episodeDownloadRepository = remember(dependencies) {
        AndroidEpisodeDownloadRepository(dependencies.offlineDownloadRepository())
    }
    val playerWindowController = remember { AndroidPlayerWindowController() }
    val offlineTitleMetadataRepository = remember(dependencies) {
        AndroidOfflineTitleMetadataRepository(dependencies.offlineTitleMetadataRepository())
    }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    val preferences = LocalAppPreferencesState.current
    androidx.compose.runtime.LaunchedEffect(
        catalogRepository,
        preferences.animeSource.value,
        externalSnapshot?.registry,
    ) {
        preferences.animeSource.value.let(catalogRepository::selectSource)
    }
    val density = LocalDensity.current
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty().ifBlank { "en" }
    val layoutEnvironment = androidSharedAppLayoutEnvironment(density)
    val sources = remember(externalSnapshot?.registry) {
        mergeAppSourceDescriptors(
            builtIn = emptyList(),
            external = externalSnapshot?.registry?.toAppSourceDescriptors().orEmpty(),
        )
    }
    CompositionLocalProvider(LocalAppLayoutEnvironment provides layoutEnvironment) {
        SharedHibikiApp(
            modifier = modifier,
            repository = catalogRepository,
            homeRepository = homeRepository,
            libraryRepository = libraryRepository,
            profileRepository = profileRepository,
            settingsStore = settingsStore,
            progressRepository = watchStateRepository,
            episodeDownloadRepository = episodeDownloadRepository,
            offlineWatchDataRepository = episodeDownloadRepository,
            offlineTitleMetadataRepository = offlineTitleMetadataRepository,
            systemLanguage = systemLanguage,
            appVersionName = BuildConfig.VERSION_NAME,
            enableOnboarding = enableOnboarding,
            catalogRefreshKey = externalSnapshot?.registry,
            platformCallbacks = AppPlatformCallbacks(
                discordRpcController = discordRpcController,
                resumeFrameContent = { titleId, frameModifier ->
                    resumeFrameRepository.getFrame(titleId)?.let { frame ->
                        AsyncImage(
                            model = frame,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = frameModifier,
                        )
                    }
                },
                onRequestOnboardingNotificationPermission = onConfigureNotifications,
                onConfigureNotifications = onConfigureNotifications,
                notificationsAvailable = true,
                onCheckForUpdates = onCheckForUpdates,
                onExportLogs = {
                    AppLogger.shareLogs(context).onFailure {
                        Toast.makeText(context, R.string.settings_export_logs_failed, Toast.LENGTH_SHORT).show()
                    }
                },
                onProfileAvatarEdit = activityLaunchers.editAvatar,
                profileAvatarEditAvailable = true,
                onOpenUrl = uriHandler::openUri,
                onDiscordBrowserSignIn = activityLaunchers.signInWithDiscord,
            ),
            sourceCallbacks = AppSourcePlatformCallbacks(
                externalSourceRepositoryController = externalRepositoryController,
                sources = sources,
                sourceConfigContent = { source, onSaved, onCancel ->
                val sourceId = SourceId(source.id)
                val draft = externalSourceConfigStore.loadDraft(sourceId)
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
                            if (field.kind == SourceConfigValueKind.SECRET) {
                                secrets[field.key]?.let { value ->
                                    if (value.isBlank()) {
                                        externalSourceConfigStore.clearSecret(sourceId, field.key)
                                    } else {
                                        externalSourceConfigStore.saveSecret(sourceId, field.key, value)
                                    }
                                }
                            } else {
                                values[field.key]?.let { value ->
                                    val normalizedValue = value.trim()
                                    if (normalizedValue.isEmpty()) {
                                        externalSourceConfigStore.clearValue(sourceId, field.key)
                                    } else {
                                        externalSourceConfigStore.saveValue(sourceId, field.key, normalizedValue)
                                    }
                                }
                            }
                        }
                        externalWatchRepository?.invalidateSource(sourceId)
                        onSaved()
                    },
                    onCancel = onCancel,
                )
                },
                selectedSourceId = preferences.animeSource.value,
                onSourceSelected = { sourceId ->
                    settingsStore.save(settingsStore.load().copy(selectedSourceId = sourceId))
                },
                readClipboardText = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                },
                copyText = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Hibiki source repository", text))
                },
            ),
            watchRepository = routedWatchRepository,
            playbackCallbacks = AppPlaybackPlatformCallbacks(
                onWatchSourceSelected = { titleId, source ->
                    watchStateRepository.savePlaybackSourceSelection(titleId, source)
                },
                onPlaybackSelectionChanged = watchStateRepository::savePlaybackSelection,
                loadPlaybackSelection = watchStateRepository::loadPlaybackSelectionOrNull,
                playbackHost = androidSharedAppPlaybackHost(
                    progressRepository = watchStateRepository,
                    windowController = playerWindowController,
                ),
                playerWindowMode = { active ->
                    AndroidSharedAppPlayerWindowMode(
                        active = active,
                        controller = playerWindowController,
                        activity = activity,
                    )
                },
            ),
        )
    }
}
