package org.akkirrai.hibiki.shared.app.shell.runtime

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.app.shell.settings.HibikiSettingsActions
import org.akkirrai.hibiki.shared.app.shell.settings.createHibikiSettingsActions
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.player.EpisodesPresenter
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.saveHibikiAppSettings
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

internal const val DEFAULT_PROFILE_NAME = "hibiki"
internal const val HOME_SEARCH_PAGE_SIZE = 24

@Composable
internal fun hibikiAppColorScheme(
    themeMode: ThemeMode,
    useAmoledTheme: Boolean,
): ColorScheme {
    val effectiveDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val baseColorScheme = if (effectiveDarkTheme) HibikiDarkColorScheme else HibikiLightColorScheme
    return if (useAmoledTheme && effectiveDarkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        baseColorScheme
    }
}

@Composable
internal fun HibikiAppDataEffects(
    libraryRepository: LibraryRepository,
    libraryPresenter: LibraryPresenter,
    selectedAnimeKey: String?,
    homeRepository: HomeDataRepository?,
    homePresenter: HomePresenter,
    setHomeState: (HomeUiState) -> Unit,
    profileRepository: LocalProfileDataRepository,
    profilePresenter: LocalProfilePresenter,
) {
    LaunchedEffect(libraryRepository, selectedAnimeKey) {
        try {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            libraryPresenter.updateEntries(emptyList())
        }
    }

    LaunchedEffect(homeRepository) {
        try {
            if (homeRepository == null) {
                homePresenter.setState(HomeUiState())
            } else {
                homePresenter.setState(homeRepository.fallbackHomeState())
                setHomeState(homeRepository.loadHomeState())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            homePresenter.setState(
                HomeUiState(
                    errorMessage = throwable.message ?: "Home loading failed",
                ),
            )
        }
    }

    LaunchedEffect(profileRepository) {
        try {
            profilePresenter.load(profileRepository)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            profilePresenter.setData(LocalProfileData())
        }
    }
}

internal fun launchLocalDataRefresh(
    scope: CoroutineScope,
    libraryPresenter: LibraryPresenter,
    libraryRepository: LibraryRepository,
    profilePresenter: LocalProfilePresenter,
    profileRepository: LocalProfileDataRepository,
    homeRepository: HomeDataRepository?,
    setHomeState: (HomeUiState) -> Unit,
) {
    scope.launch {
        try {
            libraryPresenter.updateEntries(libraryRepository.getEntries())
            profilePresenter.load(profileRepository)
            homeRepository?.let { repository ->
                setHomeState(repository.loadHomeState())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            println("Hibiki local data refresh failed: ${throwable.message ?: throwable::class.simpleName}")
        }
    }
}

internal class HibikiAppShellResources(
    val presenter: AnimeCatalogPresenter,
    val homeSearchPresenter: HomeSearchPresenter,
    val homePresenter: HomePresenter,
    val homeListState: LazyListState,
    val catalogListState: LazyListState,
    val settingsListState: LazyListState,
    val sourceSearchPresenter: SourcesSearchPresenter,
    val watchPresenter: WatchSourcesPresenter,
    val episodesPresenter: EpisodesPresenter,
    val playerPresenter: PlayerPresenter,
    val libraryPresenter: LibraryPresenter,
    val profilePresenter: LocalProfilePresenter,
    val homeDescriptionRequests: MutableSet<String>,
)

@Composable
internal fun rememberHibikiAppShellResources(
    repository: AnimeCatalogRepository,
    homeRepository: HomeDataRepository?,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    watchRepository: WatchDataRepository?,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    scope: CoroutineScope,
): HibikiAppShellResources = HibikiAppShellResources(
    presenter = remember(repository) { AnimeCatalogPresenter(repository, scope, pageSize = HOME_SEARCH_PAGE_SIZE) },
    homeSearchPresenter = remember(repository) { HomeSearchPresenter(repository, scope, pageSize = HOME_SEARCH_PAGE_SIZE) },
    homePresenter = remember(homeRepository) { HomePresenter() },
    homeListState = rememberSaveable(selectedSourceId, saver = LazyListState.Saver) { LazyListState() },
    catalogListState = rememberSaveable(selectedSourceId, saver = LazyListState.Saver) { LazyListState() },
    settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    sourceSearchPresenter = remember(repository, sources) { SourcesSearchPresenter(repository, sources, scope) },
    watchPresenter = remember(watchRepository) { WatchSourcesPresenter() },
    episodesPresenter = remember(watchRepository) { EpisodesPresenter() },
    playerPresenter = remember(watchRepository) { PlayerPresenter(PlayerUiState(isLoading = false)) },
    libraryPresenter = remember(libraryRepository) { LibraryPresenter() },
    profilePresenter = remember(profileRepository) { LocalProfilePresenter() },
    homeDescriptionRequests = remember(homeRepository) { mutableSetOf() },
)

internal class HibikiAppSettingsState(
    initial: AppSettingsState,
    selectedSourceId: String?,
    private val settingsStore: AppSettingsStore,
) {
    var languageMode by mutableStateOf(initial.languageMode)
    var darkTheme by mutableStateOf(initial.darkTheme)
    var themeMode by mutableStateOf(initial.themeMode)
    var useSystemColorScheme by mutableStateOf(initial.useSystemColorScheme)
    var useAmoledTheme by mutableStateOf(initial.useAmoledTheme)
    var autoSkipSegments by mutableStateOf(initial.autoSkipSegments)
    var autoPlayNextEpisode by mutableStateOf(initial.autoPlayNextEpisode)
    var notificationPermissionState by mutableStateOf(initial.notificationPermissionState)
    var onboardingCompleted by mutableStateOf(initial.onboardingCompleted)
    var onboardingSourceId by mutableStateOf(initial.selectedSourceId ?: selectedSourceId)
    var currentSelectedSourceId by mutableStateOf(selectedSourceId ?: initial.selectedSourceId)

    fun applyOnboardingPermission(state: NotificationPermissionState) {
        if (state != NotificationPermissionState.NOT_ASKED ||
            notificationPermissionState == NotificationPermissionState.NOT_ASKED
        ) {
            notificationPermissionState = state
        }
    }

    fun saveTo() {
        saveHibikiAppSettings(
            settingsStore = settingsStore,
            languageMode = languageMode,
            darkTheme = darkTheme,
            themeMode = themeMode,
            useSystemColorScheme = useSystemColorScheme,
            useAmoledTheme = useAmoledTheme,
            autoSkipSegments = autoSkipSegments,
            autoPlayNextEpisode = autoPlayNextEpisode,
            onboardingCompleted = onboardingCompleted,
            selectedSourceId = onboardingSourceId,
        )
    }

    fun completeOnboarding(sourceId: String) {
        onboardingSourceId = sourceId
        onboardingCompleted = true
        settingsStore.save(
            settingsStore.load().copy(
                onboardingCompleted = true,
                selectedSourceId = sourceId,
            ),
        )
    }

    fun createActions(): HibikiSettingsActions =
        createHibikiSettingsActions(
            saveSettings = ::saveTo,
            setLanguageMode = { languageMode = it },
            setDarkTheme = { darkTheme = it },
            setThemeMode = { themeMode = it },
            setUseSystemColorScheme = { useSystemColorScheme = it },
            setUseAmoledTheme = { useAmoledTheme = it },
            setAutoSkipSegments = { autoSkipSegments = it },
        )
}

@Composable
internal fun rememberHibikiAppSettingsState(
    settingsStore: AppSettingsStore,
    selectedSourceId: String?,
    onboardingNotificationPermissionState: NotificationPermissionState,
): HibikiAppSettingsState {
    val initial = remember(settingsStore) { settingsStore.load() }
    val state = remember(settingsStore, selectedSourceId, initial) {
        HibikiAppSettingsState(initial, selectedSourceId, settingsStore)
    }
    LaunchedEffect(onboardingNotificationPermissionState) {
        if (
            onboardingNotificationPermissionState != NotificationPermissionState.NOT_ASKED ||
            state.notificationPermissionState == NotificationPermissionState.NOT_ASKED
        ) {
            state.applyOnboardingPermission(onboardingNotificationPermissionState)
        }
    }
    return state
}
