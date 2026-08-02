package org.akkirrai.hibiki.desktop

import java.awt.Desktop
import java.net.URI
import java.nio.file.Paths
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.app.HibikiApp
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.layout.AppLayoutEnvironment
import org.akkirrai.hibiki.shared.layout.LocalAppLayoutEnvironment
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.source.BuiltInSources

private const val HIBIKI_GITHUB_URL = "https://github.com/akkirrai1337/hibiki"

/** Desktop entry point for the production shared shell. */
fun main() = application {
    val externalSourcePlatform = remember {
        createDesktopExternalSourceRepositoryPlatform(
            storageDirectory = Paths.get(System.getProperty("user.home"), ".hibiki"),
        )
    }
    LaunchedEffect(externalSourcePlatform) {
        runCatching { externalSourcePlatform.coordinator.refresh() }
            .onFailure { error ->
                println("BeakoKit external repository refresh failed: ${error.message}")
            }
    }
    DisposableEffect(externalSourcePlatform) {
        onDispose { externalSourcePlatform.close() }
    }
    val settingsStore = remember { DesktopSettingsStore() }
    val initialSourceId = remember(settingsStore) { settingsStore.load().selectedSourceId }
    val catalogRepository = remember(initialSourceId) { DesktopCatalogRepository(initialSourceId) }
    val homeRepository = remember(catalogRepository) { DesktopHomeRepository(catalogRepository) }
    val watchRepository = remember { DesktopAnimeWatchRepository() }
    val sources = remember {
        BuiltInSources.catalog.sources.map { info ->
            AppSourceDescriptor(
                id = info.id.value,
                name = info.name,
                language = info.primaryLanguage.tag,
                languageTags = info.languages.mapTo(linkedSetOf()) { it.tag },
                iconUrl = info.iconUrl,
                supportsPlayback = SourceCapability.PLAYBACK in info.capabilities,
                supportsSearch = true,
            )
        }
    }
    val selectedSourceId = remember(settingsStore, sources) {
        settingsStore.load().selectedSourceId ?: sources.firstOrNull()?.id
    }
    val progressRepository = remember { DesktopPlaybackProgressRepository() }
    val libraryRepository = remember { DesktopLibraryRepository() }
    val systemLanguage = Locale.getDefault().language.ifBlank { "en" }
    val profileRepository = remember(progressRepository) {
        DesktopLocalProfileDataRepository(progressRepository, libraryRepository)
    }
    Window(
        onCloseRequest = {
            catalogRepository.close()
            watchRepository.close()
            exitApplication()
        },
        title = "hibiki",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = HibikiLightColorScheme, typography = HibikiTypography) {
            CompositionLocalProvider(
                LocalAppLayoutEnvironment provides AppLayoutEnvironment(
                    isProvided = true,
                ),
            ) {
                DisposableEffect(Unit) {
                    onDispose {
                        catalogRepository.close()
                        watchRepository.close()
                    }
                }
                Surface(modifier = Modifier) {
                    HibikiApp(
                        repository = catalogRepository,
                        homeRepository = homeRepository,
                        libraryRepository = libraryRepository,
                        watchRepository = watchRepository,
                        settingsStore = settingsStore,
                        progressRepository = progressRepository,
                        profileRepository = profileRepository,
                        sources = sources,
                        selectedSourceId = selectedSourceId,
                        onSourceSelected = { sourceId ->
                            settingsStore.save(settingsStore.load().copy(selectedSourceId = sourceId))
                        },
                        onPlaybackSelectionChanged = progressRepository::savePlaybackSelection,
                        loadPlaybackSelection = progressRepository::loadPlaybackSelection,
                        onWatchSourceSelected = { titleId, source ->
                            progressRepository.savePlaybackSelection(
                                org.akkirrai.hibiki.shared.model.PlaybackSelection(
                                    titleId = titleId,
                                    sourceId = source.sourceId,
                                    sourceTitle = source.title,
                                    quality = source.qualityLabel,
                                    playerName = null,
                                ),
                            )
                        },
                        playbackHost = { playback, context, navigationState, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
                            DesktopVlcPlaybackHost(
                                playback = requireNotNull(playback),
                                context = context,
                                navigationState = navigationState,
                                settingsStore = settingsStore,
                                progressRepository = progressRepository,
                                onBack = onBack,
                                onEpisodeSelected = onEpisodeSelected,
                                onSettingsAction = onSettingsAction,
                                onOverlayEvent = onOverlayEvent,
                            )
                        },
                        systemLanguage = systemLanguage,
                        onOpenUrl = { url ->
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(URI(url))
                            }
                        },
                        onGitHubClick = {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(URI(HIBIKI_GITHUB_URL))
                            }
                        },
                        notificationsAvailable = false,
                        includeNavigationBarPadding = true,
                    )
                }
            }
        }
    }
}
