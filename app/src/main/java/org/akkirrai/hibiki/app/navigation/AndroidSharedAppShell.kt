package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.core.source.AndroidExternalSourceConfigStore
import org.akkirrai.hibiki.feature.player.AndroidCommonPlaybackHost
import org.akkirrai.hibiki.feature.player.AndroidPlayerWindowController
import org.akkirrai.hibiki.feature.player.AndroidPlayerWindowMode
import org.akkirrai.hibiki.feature.player.AndroidEpisodeDownloadRepository
import org.akkirrai.hibiki.feature.details.AndroidOfflineTitleMetadataRepository
import org.akkirrai.hibiki.feature.settings.AndroidDiscordRpcController
import org.akkirrai.hibiki.core.discord.DiscordAuthActivity
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.R
import android.widget.Toast
import coil3.compose.AsyncImage
import org.akkirrai.hibiki.shared.app.HibikiApp as SharedHibikiApp
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.AppNavigationBarMode
import org.akkirrai.hibiki.shared.layout.AppScreenEdgePolicy
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
import org.akkirrai.hibiki.shared.player.RoutingWatchDataRepository
import org.akkirrai.hibiki.shared.player.SharedAnimeWatchRepository

/** Android adapter for the shared shell with external sources kept behind the built-in path. */
@Composable
internal fun AndroidSharedAppShell(
    onCheckForUpdates: () -> Unit,
    onConfigureNotifications: () -> Unit,
    enableOnboarding: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val externalSourceConfigStore = remember(context) { AndroidExternalSourceConfigStore(context) }
    val uriHandler = LocalUriHandler.current
    var pendingAvatarCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingDiscordTokenCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let { pendingAvatarCallback?.invoke(it) }
        pendingAvatarCallback = null
    }
    val discordAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DiscordAuthActivity.tokenFromResult(result.data)?.let { token ->
                pendingDiscordTokenCallback?.invoke(token)
            }
        }
        pendingDiscordTokenCallback = null
    }
    val dependencies = remember(context) { context.hibikiDependencies() }
    val settingsStore = remember(dependencies) { dependencies.appSettingsStore() }
    val discordRpcController = remember(context) { AndroidDiscordRpcController(context) }
    val externalCoordinator = LocalExternalSourceRuntimeCoordinator.current
    val externalSnapshot = externalCoordinator?.snapshot?.collectAsState()?.value
    val externalRepositoryControllerScope = rememberCoroutineScope()
    val externalRepositoryController = remember(externalCoordinator) {
        externalCoordinator?.let {
            ExternalSourceRepositoryController(it, externalRepositoryControllerScope)
        }
    }
    DisposableEffect(externalRepositoryController) {
        onDispose { externalRepositoryController?.close() }
    }
    // External source search/details must keep redirects inside the manifest's host policy.
    val externalHttpClient = remember {
        HttpClient(OkHttp) {
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
            contextProvider = { sourceId ->
                DefaultSourceContext(
                    httpClient = externalHttpClient,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
                    config = externalSourceConfigStore.load(sourceId),
                )
            },
            statusLabels = externalStatusLabels,
        )
    }
    val builtInCatalogRepository = remember(dependencies) { dependencies.animeCatalogRepository() }
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
                client = HttpClient(OkHttp),
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
        preferences.animeSource.value?.let(catalogRepository::selectSource)
    }
    val density = LocalDensity.current
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty().ifBlank { "en" }
    val layoutEnvironment = AppLayoutEnvironment(
        isProvided = true,
        topSystemInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() },
        bottomSystemInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() },
        navigationBarMode = AppNavigationBarMode.Inset,
        edgePolicy = AppScreenEdgePolicy.ContentSafe,
    )
    val sources = remember(externalSnapshot?.registry) {
        val builtInSources = AnimeSourceRegistry.sources.map { source ->
            AppSourceDescriptor(
                id = source.id.value,
                name = source.name,
                language = source.language.toString(),
                languageTags = source.info.languages.mapTo(linkedSetOf()) { it.tag },
                iconUrl = source.iconUrl,
                supportsPlayback = source.supportsPlayback,
                supportsSearch = true,
                // Built-in settings still belong to the legacy source adapters. Do not route
                // their schema through the external-source config store.
                configSchema = SourceConfigSchema(),
            )
        }
        mergeAppSourceDescriptors(
            builtIn = builtInSources,
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
            systemLanguage = systemLanguage,
            appVersionName = BuildConfig.VERSION_NAME,
            enableOnboarding = enableOnboarding,
            onRequestOnboardingNotificationPermission = onConfigureNotifications,
            onConfigureNotifications = onConfigureNotifications,
            notificationsAvailable = true,
            onCheckForUpdates = onCheckForUpdates,
            onExportLogs = {
                AppLogger.shareLogs(context).onFailure {
                    Toast.makeText(context, R.string.settings_export_logs_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onProfileAvatarEdit = { onPicked ->
                pendingAvatarCallback = onPicked
                avatarPicker.launch(arrayOf("image/*"))
            },
            profileAvatarEditAvailable = true,
            onOpenUrl = uriHandler::openUri,
            discordRpcController = discordRpcController,
            onDiscordBrowserSignIn = { onToken ->
                pendingDiscordTokenCallback = onToken
                discordAuthLauncher.launch(Intent(context, DiscordAuthActivity::class.java))
            },
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
            onWatchSourceSelected = { titleId, source ->
                watchStateRepository.saveSelectedSource(
                    titleId = titleId,
                    sourceId = source.sourceId,
                    sourceTitle = source.title,
                    quality = source.qualityLabel,
                    playerName = null,
                    autoSelect = false,
                )
            },
            onPlaybackSelectionChanged = { selection ->
                watchStateRepository.saveSelectedSource(
                    titleId = selection.titleId,
                    sourceId = selection.sourceId,
                    sourceTitle = selection.sourceTitle,
                    quality = selection.quality,
                    playerName = selection.playerName,
                    autoSelect = false,
                )
            },
            loadPlaybackSelection = { titleId ->
                watchStateRepository.getSelectedSource(titleId).let { selection ->
                    selection.sourceId?.let { sourceId ->
                        org.akkirrai.hibiki.shared.model.PlaybackSelection(
                            titleId = titleId,
                            sourceId = sourceId,
                            sourceTitle = selection.sourceTitle.orEmpty(),
                            quality = selection.quality,
                            playerName = selection.playerName,
                        )
                    }
                }
            },
            watchRepository = routedWatchRepository,
            playbackHost = { playback, playbackContext, navigationState, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                playback?.let { readyPlayback ->
                    AndroidCommonPlaybackHost(
                        playback = readyPlayback,
                        context = playbackContext,
                        navigationState = navigationState,
                        progressRepository = watchStateRepository,
                        windowController = playerWindowController,
                        onBack = onBack,
                        onEpisodeSelected = onEpisodeSelected,
                        onSettingsAction = onSettingsAction,
                        onOverlayEvent = onOverlayEvent,
                    )
                }
            },
            playerWindowMode = { active ->
                AndroidPlayerWindowMode(active, playerWindowController)
            },
        )
    }
}
