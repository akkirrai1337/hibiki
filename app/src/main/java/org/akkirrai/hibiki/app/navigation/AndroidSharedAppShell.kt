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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.player.AndroidCommonPlaybackHost
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

/** Android adapter for the shared shell; disabled until the parity checkpoint is approved. */
@Composable
internal fun AndroidSharedAppShell(
    onCheckForUpdates: () -> Unit,
    onConfigureNotifications: () -> Unit,
    enableOnboarding: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    val catalogRepository = remember(dependencies) { dependencies.animeCatalogRepository() }
    val homeRepository = remember(dependencies) { dependencies.homeRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val profileRepository = remember(dependencies) { dependencies.localProfileRepository() }
    val watchRepository = remember(dependencies) { dependencies.animeWatchRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
    val episodeDownloadRepository = remember(dependencies) {
        AndroidEpisodeDownloadRepository(dependencies.offlineDownloadRepository())
    }
    val offlineTitleMetadataRepository = remember(dependencies) {
        AndroidOfflineTitleMetadataRepository(dependencies.offlineTitleMetadataRepository())
    }
    val resumeFrameRepository = remember(dependencies) { dependencies.resumeFrameRepository() }
    val preferences = LocalAppPreferencesState.current
    val density = LocalDensity.current
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty().ifBlank { "en" }
    val layoutEnvironment = AppLayoutEnvironment(
        isProvided = true,
        topSystemInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() },
        bottomSystemInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() },
        navigationBarMode = AppNavigationBarMode.Inset,
        edgePolicy = AppScreenEdgePolicy.ContentSafe,
    )
    val sources = remember {
        AnimeSourceRegistry.sources.map { source ->
            AppSourceDescriptor(
                id = source.id.value,
                name = source.name,
                language = source.language.toString(),
                languageTags = source.info.languages.mapTo(linkedSetOf()) { it.tag },
                iconUrl = source.iconUrl,
                supportsPlayback = source.supportsPlayback,
                supportsSearch = true,
            )
        }
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
            sources = sources,
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
            watchRepository = watchRepository,
            playbackHost = { playback, playbackContext, navigationState, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                AndroidCommonPlaybackHost(
                    playback = playback,
                    context = playbackContext,
                    navigationState = navigationState,
                    progressRepository = watchStateRepository,
                    onBack = onBack,
                    onEpisodeSelected = onEpisodeSelected,
                    onSettingsAction = onSettingsAction,
                    onOverlayEvent = onOverlayEvent,
                )
            },
            playerWindowMode = { active ->
                AndroidPlayerWindowMode(active)
            },
        )
    }
}
