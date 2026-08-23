package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.app.settings.isEnglishAppLanguage
import org.akkirrai.hibiki.core.network.ChallengeSessionProviderImpl
import org.akkirrai.hibiki.core.source.ExternalSourceConfigStore
import org.akkirrai.hibiki.core.source.preferredSourceLanguages
import org.akkirrai.hibiki.core.source.EmbedWebViewExtractor
import org.akkirrai.hibiki.app.settings.AppSettingsStoreImpl
import org.akkirrai.hibiki.player.CommonPlaybackHost
import org.akkirrai.hibiki.player.PlayerWindowController
import org.akkirrai.hibiki.player.PlayerWindowMode
import org.akkirrai.hibiki.player.EpisodeDownloadRepositoryImpl
import org.akkirrai.hibiki.details.data.OfflineTitleMetadataRepositoryImpl
import org.akkirrai.hibiki.app.settings.DiscordRpcControllerImpl
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.R
import android.widget.Toast
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.app.shell.HibikiAppShell
import org.akkirrai.hibiki.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.AppSourceConfigLabels
import org.akkirrai.hibiki.core.source.AppSourceConfigScreen
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText
import org.akkirrai.hibiki.core.source.ExternalAnimeStatusLabels
import org.akkirrai.hibiki.core.source.ExternalSourceRepositoryController
import org.akkirrai.hibiki.core.source.mergeAppSourceDescriptors
import org.akkirrai.hibiki.core.source.toAppSourceDescriptors
import org.akkirrai.beakokit.api.SourceConfigValueKind
import org.akkirrai.beakokit.api.SourceConfigSchema
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.catalog.ExternalSourceCatalogRepository
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.hibiki.core.source.extension.PackageManagerSourceCatalog
import org.akkirrai.hibiki.core.source.extension.repository.ApkSourceRepositoryActions
import org.akkirrai.hibiki.core.source.extension.repository.SourceExtensionInstaller
import org.akkirrai.hibiki.core.source.extension.repository.SourceRepositoryClient
import org.akkirrai.hibiki.home.data.CatalogBackedHomeDataRepository
import org.akkirrai.hibiki.player.SharedAnimeWatchRepository
import org.akkirrai.hibiki.player.AppPlaybackPlatformCallbacks
import org.akkirrai.hibiki.app.shell.AppPlatformCallbacks
import org.akkirrai.hibiki.core.source.AppSourcePlatformCallbacks

/** Android composition root for HibikiAppShell, backed entirely by external sources. */
@Composable
internal fun HibikiApp(
    activity: Activity,
    onCheckForUpdates: () -> Unit,
    onConfigureNotifications: () -> Unit,
    enableOnboarding: Boolean = false,
    settingsStoreOverride: AppSettingsStoreImpl? = null,
    onFirstContentReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = LocalAppPreferencesState.current
    val appPreferences = LocalAppPreferences.current
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty().ifBlank { "en" }
    // Drives which language external sources are asked to return catalog/details text in --
    // must track the app's own language setting so e.g. episode-count wording and release
    // season names match the UI instead of always defaulting to whichever the source itself
    // happens to prefer.
    val preferEnglishForSources = isEnglishAppLanguage(preferences.languageMode, systemLanguage)
    val externalSourceConfigStore = remember(context) { ExternalSourceConfigStore(context) }
    val androidChallengeSessionProvider = remember(context) { ChallengeSessionProviderImpl(context) }
    val embedWebViewExtractor = remember(context) { EmbedWebViewExtractor(context) }
    val activityLaunchers = rememberAppActivityLaunchers(context)
    val dependencies = remember(context) { context.hibikiDependencies() }
    val settingsStore = settingsStoreOverride ?: remember(dependencies) { dependencies.appSettingsStore() }
    val discordRpcController = remember(context) { DiscordRpcControllerImpl(context) }
    var installedExtensionsRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                installedExtensionsRevision++
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val packageManagerSourceProbe = remember(context, installedExtensionsRevision) {
        PackageManagerSourceCatalog.buildProbe(context)
    }
    val packageManagerSourceCatalog = packageManagerSourceProbe.catalog
    val currentPackageManagerSourceProbe by rememberUpdatedState(packageManagerSourceProbe)
    fun mergedExternalRegistry(): ExternalSourceRegistry = ExternalSourceRegistry(packageManagerSourceCatalog)
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
    val sourceRepositoryClient = remember(externalHttpClient) { SourceRepositoryClient(externalHttpClient) }
    val sourceExtensionInstaller = remember(context, externalHttpClient) {
        SourceExtensionInstaller(context, externalHttpClient)
    }
    val externalRepositoryControllerScope = rememberCoroutineScope()
    val externalRepositoryController = remember(context, sourceRepositoryClient, sourceExtensionInstaller) {
        ExternalSourceRepositoryController(
            actions = ApkSourceRepositoryActions(
                context,
                sourceRepositoryClient,
                sourceExtensionInstaller,
                sourceInfoByPackageName = { currentPackageManagerSourceProbe.infoByPackageName },
            ),
            scope = externalRepositoryControllerScope,
            operationContext = Dispatchers.IO,
        )
    }
    DisposableEffect(externalRepositoryController) {
        onDispose { externalRepositoryController.close() }
    }
    androidx.compose.runtime.LaunchedEffect(installedExtensionsRevision) {
        // Installs/uninstalls hand off to the system confirmation dialog and complete
        // asynchronously -- re-read package state once Android actually reports the change.
        if (installedExtensionsRevision > 0) {
            externalRepositoryController.refreshRepositories()
        }
    }
    val externalStatusLabels = ExternalAnimeStatusLabels(
        unknown = appText(AppTextKey.Unknown),
        ongoing = appText(AppTextKey.Ongoing),
        released = appText(AppTextKey.Released),
        announcement = appText(AppTextKey.Announcement),
    )
    val externalCatalogRepository = remember(
        packageManagerSourceCatalog,
        externalHttpClient,
        externalStatusLabels,
        preferEnglishForSources,
    ) {
        ExternalSourceCatalogRepository(
            registryProvider = { mergedExternalRegistry() },
            registryAwaiter = { mergedExternalRegistry() },
            contextProvider = { sourceId ->
                DefaultSourceContext(
                    httpClient = externalHttpClient,
                    preferredLanguages = preferredSourceLanguages(preferEnglishForSources),
                    config = externalSourceConfigStore.load(sourceId),
                    logger = SourceLogger { level, message, throwable ->
                        val tag = "BeakoKit/${sourceId.value}"
                        when (level) {
                            SourceLogLevel.DEBUG -> AppLogger.d(tag, message)
                            SourceLogLevel.WARNING -> AppLogger.w(tag, message, throwable)
                            SourceLogLevel.ERROR -> AppLogger.e(tag, message, throwable)
                        }
                    },
                    challengeSessionProvider = androidChallengeSessionProvider,
                )
            },
            statusLabels = externalStatusLabels,
        )
    }
    // ExternalSourceCatalogRepository is already a complete AnimeCatalogRepository on its own
    // (all sources are JVM APK extensions now, there is no separate built-in catalog to merge).
    val catalogRepository = externalCatalogRepository
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val profileRepository = remember(dependencies) { dependencies.localProfileRepository() }
    val externalWatchRepository = remember(
        packageManagerSourceCatalog,
    ) {
        SharedAnimeWatchRepository(
            client = HttpClient(OkHttp) {
                installBeakoKitHttpDefaults(
                    BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Android external-playback"),
                )
            },
            sourceHttpClient = externalHttpClient,
            externalSourceFactory = { sourceId, sourceContext ->
                val registry = mergedExternalRegistry()
                if (registry.sources.any { it.id == sourceId }) registry.create(sourceId, sourceContext) else null
            },
            sourceConfigProvider = externalSourceConfigStore::load,
            challengeSessionProvider = androidChallengeSessionProvider,
            additionalExtractors = listOf(embedWebViewExtractor),
        )
    }
    DisposableEffect(externalWatchRepository) {
        onDispose { externalWatchRepository?.close() }
    }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val homeRepository = remember(catalogRepository, libraryRepository, watchStateRepository) {
        CatalogBackedHomeDataRepository(
            catalogRepository = catalogRepository,
            libraryRepository = libraryRepository,
            watchStateRepository = watchStateRepository,
        )
    }
    val episodeDownloadRepository = remember(dependencies) {
        EpisodeDownloadRepositoryImpl(dependencies.offlineDownloadRepository())
    }
    val playerWindowController = remember { PlayerWindowController() }
    val offlineTitleMetadataRepository = remember(dependencies) {
        OfflineTitleMetadataRepositoryImpl(dependencies.offlineTitleMetadataRepository())
    }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    androidx.compose.runtime.LaunchedEffect(
        catalogRepository,
        preferences.animeSource.value,
    ) {
        preferences.animeSource.value.let(catalogRepository::selectSource)
    }
    val density = LocalDensity.current
    val layoutEnvironment = appLayoutEnvironment(density)
    val sources = remember(packageManagerSourceCatalog) {
        mergeAppSourceDescriptors(
            builtIn = emptyList(),
            external = ExternalSourceRegistry(packageManagerSourceCatalog).toAppSourceDescriptors(),
        ).also { descriptors ->
            AppLogger.d(
                "BeakoKit/sources",
                "descriptors=" + descriptors.joinToString { "${it.id}:iconUrl=${it.iconUrl}" },
            )
        }
    }
    CompositionLocalProvider(LocalAppLayoutEnvironment provides layoutEnvironment) {
        val sourceCallbacks = AppSourcePlatformCallbacks(
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
                appPreferences.setAnimeSource(SourceId(sourceId))
                settingsStore.save(settingsStore.load().copy(selectedSourceId = sourceId))
            },
        )
        HibikiAppShell(
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
            catalogRefreshKey = installedExtensionsRevision.toString(),
            catalogReady = true,
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
                onDiscordBrowserSignIn = activityLaunchers.signInWithDiscord,
                onFirstContentReady = onFirstContentReady,
            ),
            sourceCallbacks = sourceCallbacks,
            watchRepository = externalWatchRepository,
            playbackCallbacks = AppPlaybackPlatformCallbacks(
                onWatchSourceSelected = { titleId, source ->
                    watchStateRepository.savePlaybackSourceSelection(titleId, source)
                },
                onPlaybackSelectionChanged = watchStateRepository::savePlaybackSelection,
                loadPlaybackSelection = watchStateRepository::loadPlaybackSelectionOrNull,
                playbackHost = { playback, playbackContext, navigationState, playbackLoading, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                    CommonPlaybackHost(
                        playback = playback,
                        context = playbackContext,
                        navigationState = navigationState,
                        playbackLoading = playbackLoading,
                        progressRepository = watchStateRepository,
                        resumeFrameRepository = resumeFrameRepository,
                        windowController = playerWindowController,
                        onBack = onBack,
                        onEpisodeSelected = onEpisodeSelected,
                        onSettingsAction = onSettingsAction,
                        onOverlayEvent = onOverlayEvent,
                    )
                },
                playerWindowMode = { active ->
                    PlayerWindowMode(
                        active = active,
                        controller = playerWindowController,
                        activity = activity,
                    )
                },
                onExitPlayback = playerWindowController::persist,
            ),
        )
    }
}
