package org.akkirrai.hibiki.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import org.akkirrai.hibiki.BuildConfig
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.player.AndroidCommonPlaybackHost
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dependencies = remember(context) { context.hibikiDependencies() }
    val settingsStore = remember(dependencies) { dependencies.appSettingsStore() }
    val catalogRepository = remember(dependencies) { dependencies.animeCatalogRepository() }
    val homeRepository = remember(dependencies) { dependencies.homeRepository() }
    val libraryRepository = remember(dependencies) { dependencies.libraryRepository() }
    val profileRepository = remember(dependencies) { dependencies.localProfileRepository() }
    val watchRepository = remember(dependencies) { dependencies.animeWatchRepository() }
    val watchStateRepository = remember(dependencies) { dependencies.watchStateRepository() }
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
            systemLanguage = systemLanguage,
            appVersionName = BuildConfig.VERSION_NAME,
            onConfigureNotifications = onConfigureNotifications,
            sources = sources,
            selectedSourceId = preferences.animeSource.value.takeIf { preferences.hasExplicitAnimeSource },
            onSourceSelected = { sourceId ->
                settingsStore.save(settingsStore.load().copy(selectedSourceId = sourceId))
            },
            watchRepository = watchRepository,
            playbackHost = { playback, playbackContext, onBack, onEpisodeSelected, onSettingsAction ->
                AndroidCommonPlaybackHost(
                    playback = playback,
                    context = playbackContext,
                    progressRepository = watchStateRepository,
                    onBack = onBack,
                    onEpisodeSelected = onEpisodeSelected,
                    onSettingsAction = onSettingsAction,
                )
            },
        )
    }
}
