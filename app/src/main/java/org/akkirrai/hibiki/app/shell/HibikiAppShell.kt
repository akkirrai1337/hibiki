
package org.akkirrai.hibiki.app.shell

import org.akkirrai.hibiki.player.HibikiPlaybackSession
import org.akkirrai.hibiki.app.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.home.state.launchHomeDescriptionEnrichment
import org.akkirrai.hibiki.app.destination.content.AppDestinationContent
import org.akkirrai.hibiki.app.destination.content.AppDestinationTabContent
import org.akkirrai.hibiki.app.destination.context.*
import org.akkirrai.hibiki.app.destination.watch.*
import org.akkirrai.hibiki.core.source.AppSourceCallbacks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.app.shell.navigation.*
import org.akkirrai.hibiki.app.shell.settings.*
import org.akkirrai.hibiki.app.destination.settings.*
import org.akkirrai.hibiki.app.destination.source.*
import org.akkirrai.hibiki.app.destination.home.*
import org.akkirrai.hibiki.app.shell.catalog.*
import org.akkirrai.hibiki.app.shell.player.*
import org.akkirrai.hibiki.app.shell.player.watch.*
import org.akkirrai.hibiki.app.shell.layout.*
import org.akkirrai.hibiki.app.shell.runtime.*
import org.akkirrai.hibiki.app.destination.library.*
import org.akkirrai.hibiki.app.shell.overlay.*
import org.akkirrai.hibiki.app.shell.source.*
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.catalog.screen.CatalogActions
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.catalog.presentation.CatalogDetailsNavigationState
import org.akkirrai.hibiki.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.catalog.EmptyAnimeCatalogRepository
import org.akkirrai.hibiki.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.design.HibikiLightColorScheme
import org.akkirrai.hibiki.design.HibikiTypography
import org.akkirrai.hibiki.player.model.TitleWatchState
import org.akkirrai.hibiki.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.library.LibraryRepository
import org.akkirrai.hibiki.library.screen.LibraryActions
import org.akkirrai.hibiki.home.state.HomeUiState
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.home.screen.HomeActions
import org.akkirrai.hibiki.home.state.preserveLoadedDescriptions as preserveHomeDescriptions
import org.akkirrai.hibiki.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.profile.LocalProfileData
import org.akkirrai.hibiki.profile.LocalProfilePresenter
import org.akkirrai.hibiki.app.settings.ThemeMode
import org.akkirrai.hibiki.app.settings.AppSettingsStore
import org.akkirrai.hibiki.app.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.app.settings.NotificationPermissionState
import org.akkirrai.hibiki.app.settings.DiscordRpcUiState
import org.akkirrai.hibiki.app.settings.SettingsScreenState
import org.akkirrai.hibiki.app.settings.SettingsScreenActions
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.activeOverlay
import org.akkirrai.hibiki.app.navigation.appBackHandlerEnabled
import org.akkirrai.hibiki.app.navigation.appBottomBarVisible
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.appShellTransitionKey
import org.akkirrai.hibiki.app.navigation.isWatchFlowRoute
import org.akkirrai.hibiki.app.navigation.currentRoute
import org.akkirrai.hibiki.app.navigation.currentTransitionKey
import org.akkirrai.hibiki.app.navigation.selectedWatchSource
import org.akkirrai.hibiki.app.navigation.reduce
import org.akkirrai.hibiki.app.navigation.selectedAppDestination
import org.akkirrai.hibiki.app.navigation.toTopLevelDestination
import org.akkirrai.hibiki.app.navigation.toAppDestination
import org.akkirrai.hibiki.player.PlayerPresenter
import org.akkirrai.hibiki.player.PlayerUiState
import org.akkirrai.hibiki.player.AppPlaybackCallbacks
import org.akkirrai.hibiki.player.EpisodesUiState
import org.akkirrai.hibiki.player.WatchDataRepository
import org.akkirrai.hibiki.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.player.OfflineWatchDataRepository

import org.akkirrai.hibiki.player.WatchSourcesPresenter
import org.akkirrai.hibiki.player.shouldShowPlaybackHost
import org.akkirrai.hibiki.app.navigation.navigateToEpisodes
import org.akkirrai.hibiki.app.navigation.navigateToSettings
import org.akkirrai.hibiki.app.navigation.navigateToPlayer
import org.akkirrai.hibiki.app.navigation.navigateToWatchSources
import org.akkirrai.hibiki.app.navigation.reduceOverlayVisibilityChange
import org.akkirrai.hibiki.player.WatchSourcesScreenState
import org.akkirrai.hibiki.player.errorEpisodesState
import org.akkirrai.hibiki.player.initialEpisodesState
import org.akkirrai.hibiki.player.initialWatchSourcesState
import org.akkirrai.hibiki.player.loadedEpisodesState
import org.akkirrai.hibiki.player.beginPlaybackLoad
import org.akkirrai.hibiki.player.withPlaybackError
import org.akkirrai.hibiki.player.resetForNavigation
import org.akkirrai.hibiki.player.withPlaybackLoaded
import org.akkirrai.hibiki.player.withLoadedSources
import org.akkirrai.hibiki.player.withWatchSourcesError
import org.akkirrai.hibiki.player.resolveResumeWatchState
import org.akkirrai.hibiki.player.showAllWatchSources
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackRoute
import androidx.activity.compose.BackHandler

@Composable
internal fun HibikiAppShell(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = EmptyAnimeCatalogRepository,
    homeRepository: HomeDataRepository? = null,
    libraryRepository: LibraryRepository,
    profileRepository: LocalProfileDataRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
    progressRepository: PlaybackProgressRepository? = null,
    episodeDownloadRepository: EpisodeDownloadRepository? = null,
    offlineWatchDataRepository: OfflineWatchDataRepository? = null,
    offlineTitleMetadataRepository: OfflineTitleMetadataRepository? = null,
    systemLanguage: String = "en",
    appVersionName: String = "dev",
    enableOnboarding: Boolean = false,
    onboardingNotificationPermissionState: NotificationPermissionState = NotificationPermissionState.NOT_ASKED,
    platformCallbacks: AppShellCallbacks = AppShellCallbacks(),
    sourceCallbacks: AppSourceCallbacks = AppSourceCallbacks(),
    watchRepository: WatchDataRepository? = null,
    playbackCallbacks: AppPlaybackCallbacks = AppPlaybackCallbacks(),
    catalogRefreshKey: Any? = null,
    catalogReady: Boolean = true,
) {
    val scope = rememberCoroutineScope {
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                println("Hibiki coroutine failed: ${throwable.message ?: throwable::class.simpleName}")
            }
        }
    }
    val resources = rememberHibikiAppShellResources(
        repository = repository,
        homeRepository = homeRepository,
        libraryRepository = libraryRepository,
        profileRepository = profileRepository,
        watchRepository = watchRepository,
        sources = sourceCallbacks.sources,
        selectedSourceId = sourceCallbacks.selectedSourceId,
        scope = scope,
    )
    val presenter = resources.presenter
    val catalogActions = HibikiCatalogActions(presenter)
    val detailsNavState by presenter.detailsNavigationState.collectAsState(initial = CatalogDetailsNavigationState())
    val homeSearchPresenter = resources.homeSearchPresenter
    val homeSearchActions = HibikiHomeSearchActions(homeSearchPresenter)
    val homePresenter = resources.homePresenter

    fun setHomeStatePreservingDescriptions(state: HomeUiState) {
        homePresenter.setState(state.preserveHomeDescriptions(homePresenter.state.value))
    }
    val homeListState = resources.homeListState
    val catalogListState = resources.catalogListState
    val settingsListState = resources.settingsListState
    val libraryListState = resources.libraryListState
    val sourceSearchPresenter = resources.sourceSearchPresenter
    val watchPresenter = resources.watchPresenter
    val watchFlowState = rememberHibikiWatchFlowState()
    var detailsAnime by watchFlowState::detailsAnime
    val watchAnime = detailsAnime ?: detailsNavState.selectedAnime
    var watchLoadGeneration by watchFlowState::watchLoadGeneration
    var forceWatchSourcesRefresh by watchFlowState::forceWatchSourcesRefresh
    var episodesLoadGeneration by watchFlowState::episodesLoadGeneration
    // Set by HibikiWatchDataEffects when sources finish loading with exactly one voiceover;
    // consumed by a LaunchedEffect further below once watchFlowNavigationActions exists.
    var singleWatchSourceCandidate by remember { mutableStateOf<org.akkirrai.hibiki.player.model.WatchSource?>(null) }
    val episodesPresenter = resources.episodesPresenter
    val shellNavigationState = rememberHibikiAppShellNavigationState()
    var navigationState by shellNavigationState.route
    val libraryFilterOverlay = shellNavigationState.libraryFilterOverlay
    val isLibraryFilterOverlayOpen = navigationState.activeOverlay == libraryFilterOverlay
    val selectedWatchSource = navigationState.selectedWatchSource
    val playerPresenter = resources.playerPresenter
    val playerState by playerPresenter.state.collectAsState()
    val playbackSession = remember { HibikiPlaybackSession() }
    SideEffect { playbackSession.setOnExitPlayback(playbackCallbacks.onExitPlayback) }
    var selectedSourcesTab by rememberSaveable { mutableStateOf(0) }
    var playbackJob by playbackSession.job
    var homeRefreshJob by remember { mutableStateOf<Job?>(null) }
    var activePlaybackRoute by playbackSession.activeRoute
    // getAllPlaybackProgress() is a plain (non-suspend) SharedPreferences read, cheap enough to
    // compute directly during composition -- doing it here instead of inside a LaunchedEffect
    // (as it was before) means the "Continue watching" banner has the right value on Details'
    // very first composed frame, rather than popping in/changing one frame after a coroutine
    // dispatch, which read as a flicker on every entry.
    val detailsResumeState = remember(progressRepository, detailsNavState.selectedAnime?.id, activePlaybackRoute == null) {
        val animeId = detailsNavState.selectedAnime?.id
        if (progressRepository != null && animeId != null) {
            resolveResumeWatchState(
                progressRepository.getAllPlaybackProgress().filter { it.titleId == animeId },
            )
        } else {
            null
        }
    }
    var pendingPlaybackContext by playbackSession.pendingContext
    var playbackReturnRoute by playbackSession.returnRoute
    val libraryPresenter = resources.libraryPresenter
    val libraryActions = HibikiLibraryActions(libraryPresenter)
    val profilePresenter = resources.profilePresenter
    val homeDescriptionRequests = resources.homeDescriptionRequests
    val discordRpcState by (platformCallbacks.discordRpcController?.state ?: kotlinx.coroutines.flow.MutableStateFlow(DiscordRpcUiState())).collectAsState()
    val discordAuthState = rememberHibikiDiscordAuthState()
    val isDiscordAuthDialogOpen = discordAuthState.isOpen(navigationState)

    fun openDiscordAuthDialog() {
        discordAuthState.open(navigationState) { navigationState = it }
    }

    fun closeDiscordAuthDialog() {
        discordAuthState.close(navigationState) { navigationState = it }
    }
    val discordSettingsActions = createHibikiDiscordSettingsActions(
        controller = platformCallbacks.discordRpcController,
        openAuthDialog = ::openDiscordAuthDialog,
    )
    val routePresentation = navigationState.toHibikiShellRoutePresentation()
    val selectedTab = routePresentation.selectedTab
    // Only refresh once per real visit, not every time the user returns to an already-loaded
    // Sources tab -- manual pull-to-refresh is wired separately via onRefresh.
    var hasRefreshedSourceRepositories by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab, sourceCallbacks.externalSourceRepositoryController) {
        if (selectedTab == AppDestination.SOURCES && !hasRefreshedSourceRepositories) {
            hasRefreshedSourceRepositories = true
            sourceCallbacks.externalSourceRepositoryController?.refreshRepositories()
        }
    }
    val appSettingsState = rememberHibikiAppSettingsState(
        settingsStore = settingsStore,
        selectedSourceId = sourceCallbacks.selectedSourceId,
        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
    )
    val sourceSelectionCoordinator = HibikiSourceSelectionCoordinator(
        repository = repository,
        presenter = presenter,
        homeSearchPresenter = homeSearchPresenter,
        sourceSearchPresenter = sourceSearchPresenter,
        homeRepository = homeRepository,
        homePresenter = homePresenter,
        scope = scope,
        onSourceSelected = sourceCallbacks.onSourceSelected,
        setHomeState = ::setHomeStatePreservingDescriptions,
        setCurrentSourceId = { appSettingsState.currentSelectedSourceId = it },
        getJob = { homeRefreshJob },
        setJob = { homeRefreshJob = it },
    )

    HibikiCatalogPresenterLifecycle(
        presenter = presenter,
        homeSearchPresenter = homeSearchPresenter,
        sourceSearchPresenter = sourceSearchPresenter,
        catalogRefreshKey = catalogRefreshKey,
        catalogReady = catalogReady,
        catalogVisible = selectedTab == AppDestination.CATALOG,
        onDisposed = sourceSelectionCoordinator::cancel,
    )

    HibikiAppDataEffects(
        libraryRepository = libraryRepository,
        libraryPresenter = libraryPresenter,
        homeRepository = homeRepository,
        homePresenter = homePresenter,
        setHomeState = ::setHomeStatePreservingDescriptions,
        profileRepository = profileRepository,
        profilePresenter = profilePresenter,
        onHomeContentReady = platformCallbacks.onFirstContentReady,
    )

    HibikiWatchDataEffects(
        selectedAnime = detailsNavState.selectedAnime,
        detailsLoading = detailsNavState.isDetailsLoading,
        offlineTitleMetadataRepository = offlineTitleMetadataRepository,
        onDetailsAnimeChanged = { detailsAnime = it },
        watchAnime = watchAnime,
        watchRepository = watchRepository,
        offlineWatchDataRepository = offlineWatchDataRepository,
        watchPresenter = watchPresenter,
        watchLoadGeneration = watchLoadGeneration,
        forceWatchSourcesRefresh = forceWatchSourcesRefresh,
        activePlaybackRoute = activePlaybackRoute,
        onPlaybackRouteChanged = { activePlaybackRoute = it },
        selectedWatchSource = selectedWatchSource,
        episodesPresenter = episodesPresenter,
        episodesLoadGeneration = episodesLoadGeneration,
        onSingleWatchSourceLoaded = { source -> singleWatchSourceCandidate = source },
    )
    val homeActions = createHibikiHomeActions(
        repository = homeRepository,
        presenter = homePresenter,
        requests = homeDescriptionRequests,
        scope = scope,
        setHomeState = ::setHomeStatePreservingDescriptions,
    )

    val playbackEffects = HibikiAppShellPlaybackEffects(
        playerPresenter = playerPresenter,
        invalidateEpisodes = { episodesLoadGeneration++ },
    )

    val refreshLocalData = {
        launchLocalDataRefresh(
            scope = scope,
            libraryPresenter = libraryPresenter,
            libraryRepository = libraryRepository,
            profilePresenter = profilePresenter,
            profileRepository = profileRepository,
            homeRepository = homeRepository,
            setHomeState = { refreshedState ->
                setHomeStatePreservingDescriptions(refreshedState)
            },
        )
        Unit
    }

    val playbackRequestCoordinator = HibikiPlaybackRequestCoordinator(
        scope = scope,
        watchRepository = { watchRepository },
        offlineWatchDataRepository = offlineWatchDataRepository,
        offlineTitleMetadataRepository = offlineTitleMetadataRepository,
        progressRepository = progressRepository,
        playerPresenter = playerPresenter,
        playbackSession = playbackSession,
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        selectedWatchSource = { selectedWatchSource },
        watchAnime = { watchAnime },
        episodesState = { episodesPresenter.state.value },
        activePlaybackRoute = { activePlaybackRoute },
        loadPlaybackSelection = playbackCallbacks.loadPlaybackSelection,
        onPlaybackSelectionChanged = playbackCallbacks.onPlaybackSelectionChanged,
        onPlaybackReady = playbackCallbacks.onPlaybackReady,
        playbackHost = { playbackCallbacks.playbackHost != null },
        onPendingPlaybackContextChanged = { pendingPlaybackContext = it },
        onPlaybackPublished = playbackCallbacks.playbackHost?.let { { playback, context ->
            playbackSession.publishPlayback(playback, context)
        } },
        setPlaybackJob = { playbackJob = it },
        onSourceSelected = playbackCallbacks.onWatchSourceSelected,
        // Also persist immediately (matching HibikiSettingsActions.onAutoSkipChange's own
        // set+save pattern for the Settings screen) -- appSettingsState is a separate in-memory
        // cache from the AppPreferences store the player itself reads via
        // LocalAppPreferencesState, and nothing else was flushing this particular change through.
        setAutoSkipSegments = { appSettingsState.autoSkipSegments = it; appSettingsState.saveTo() },
        setAutoPlayNextEpisode = { appSettingsState.autoPlayNextEpisode = it; appSettingsState.saveTo() },
        libraryRepository = libraryRepository,
        onLibraryChanged = refreshLocalData,
    )

    val watchRetryActions = HibikiWatchRetryActions(
        lastPlaybackRequest = { playerState.lastPlaybackRequest },
        hasPlaybackError = { playerState.errorMessage != null },
        selectedWatchSource = { selectedWatchSource },
        retryPlayback = { failedRequest ->
            playbackRequestCoordinator.request(
                episode = failedRequest.episode,
                sourceOverride = failedRequest.source,
                preferredPlayerName = failedRequest.preferredPlayerName,
                preferredQuality = failedRequest.preferredQuality,
                forceRefresh = true,
            )
        },
        retryWatchSources = {
            forceWatchSourcesRefresh = true
            watchLoadGeneration++
        },
        retryEpisodes = { episodesLoadGeneration++ },
    )

    val detailsNavigationActions = HibikiDetailsNavigationActions(
        presenter = presenter,
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        setDetailsAnime = { detailsAnime = it },
    )
    val overlayActions = HibikiOverlayActions(
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        libraryFilterOverlay = libraryFilterOverlay,
    )
    val watchFlowNavigationActions = HibikiWatchFlowNavigationActions(
        watchPresenter = watchPresenter,
        offlineWatchDataRepository = offlineWatchDataRepository,
        episodesPresenter = episodesPresenter,
        playbackSession = playbackSession,
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        getPlaybackReturnRoute = { playbackReturnRoute },
        setPlaybackReturnRoute = { playbackReturnRoute = it },
        setForceWatchSourcesRefresh = { forceWatchSourcesRefresh = it },
        incrementWatchLoadGeneration = { watchLoadGeneration++ },
        onSourceSelected = playbackCallbacks.onWatchSourceSelected,
        resetPlayerState = playbackEffects::resetPlayerState,
        applyBackEffect = playbackEffects::applyWatchFlowBackEffect,
    )
    LaunchedEffect(singleWatchSourceCandidate) {
        singleWatchSourceCandidate?.let { source ->
            watchFlowNavigationActions.autoSelectSingleWatchSource(
                source = source,
                animeId = watchAnime?.id,
                downloadMode = navigationState.currentRoute.downloadModeEnabled(),
            )
            singleWatchSourceCandidate = null
        }
    }
    val playbackOverlayActions = HibikiAppShellPlaybackOverlayActions(
        playbackSession = playbackSession,
        playbackEffects = playbackEffects,
        playbackRequestCoordinator = playbackRequestCoordinator,
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        playbackReturnRoute = { playbackReturnRoute },
        setPlaybackReturnRoute = { playbackReturnRoute = it },
    )
    val systemBackCoordinator = createHibikiSystemBackCoordinator(
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
        selectedTab = { selectedTab },
        playbackSession = playbackSession,
        hasActivePlayback = { activePlaybackRoute != null },
        playbackReturnRoute = { playbackReturnRoute },
        setPlaybackReturnRoute = { playbackReturnRoute = it },
        applyWatchFlowBackEffect = playbackEffects::applyWatchFlowBackEffect,
        closeDetails = presenter::closeDetails,
    )

    MaterialTheme(
            colorScheme = hibikiAppColorScheme(appSettingsState.themeMode, appSettingsState.useAmoledTheme),
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                playbackCallbacks.playerWindowMode(routePresentation.isPlayerRoute)
                BackHandler(
                    enabled = appBackHandlerEnabled(
                        state = navigationState,
                    ),
                    onBack = systemBackCoordinator::handle,
                )
                run {
                Box {
                    val settingsActions = appSettingsState.createActions()
                    val topLevelDestination = routePresentation.topLevelDestination
                    val activeDownloadMode = routePresentation.activeDownloadMode
                    val rootNavigationCoordinator = createHibikiRootNavigationCoordinator(
                        navigationState = { navigationState },
                        setNavigationState = { navigationState = it },
                        selectedTab = { selectedTab },
                        presenter = presenter,
                        setDetailsAnime = { detailsAnime = it },
                        playbackSession = playbackSession,
                        episodesPresenter = episodesPresenter,
                        resetPlayerState = playbackEffects::resetPlayerState,
                    )
                    val navigationActions = createHibikiAppShellDestinationNavigationActions(
                        detailsNavigationActions = detailsNavigationActions,
                        watchFlowNavigationActions = watchFlowNavigationActions,
                        rootNavigationCoordinator = rootNavigationCoordinator,
                        activeDownloadMode = activeDownloadMode,
                        updateNavigationState = { reduce ->
                            navigationState = reduce(navigationState)
                        },
                        onSourceSelected = sourceSelectionCoordinator::select,
                    )
                    fun destinationInputFor(tab: AppDestination): AppDestinationContentInput = AppDestinationContentInput(
                        selectedTab = tab,
                        catalog = CatalogContentInput(
                            presenter = presenter,
                            listState = catalogListState,
                            actions = CatalogActions(
                                onQueryChange = catalogActions.onQueryChange,
                                onRetry = catalogActions.onRetry,
                                onRefresh = catalogActions.onRefresh,
                                onLoadMoreRetry = catalogActions.onLoadMoreRetry,
                                onItemVisible = {},
                                onSortSelected = catalogActions.onSortSelected,
                                onFiltersApply = catalogActions.onFiltersChange,
                                onAnimeClick = navigationActions.onAnimeClick,
                            ),
                        ),
                        home = HomeContentInput(
                            state = AppDestinationHomeState(
                                homePresenter = homePresenter,
                                homeSearchPresenter = homeSearchPresenter,
                                listState = homeListState,
                            ),
                            actions = HomeActions(
                                onQueryChange = homeSearchActions.onQueryChange,
                                onClearSearch = homeSearchActions.onClear,
                                onFilterApply = homeSearchActions.onFilterApply,
                                onLoadMoreSearch = homeSearchActions.onLoadMore,
                                onRetrySearch = homeSearchActions.onRetry,
                                onAnimeClick = navigationActions.onAnimeClick,
                                onBrowseCatalog = navigationActions.onBrowseCatalog,
                                onOpenLibrary = navigationActions.onOpenLibrary,
                                onItemVisible = homeActions.onItemVisible,
                            ),
                            onRefresh = homeActions.onRefresh,
                        ),
                        library = LibraryContentInput(
                            state = AppDestinationLibraryState(
                                libraryPresenter = libraryPresenter,
                                filterOverlayOpen = isLibraryFilterOverlayOpen,
                                listState = libraryListState,
                            ),
                            actions = LibraryActions(
                                onAnimeClick = navigationActions.onAnimeClick,
                                onSearchQueryChange = libraryActions.onSearchQueryChange,
                                onClearSearch = libraryActions.onSearchClear,
                                onFilterClick = { overlayActions.setLibraryFilterVisible(true) },
                                onCategorySelected = libraryActions.onCategorySelected,
                                onFilterVisibilityChange = overlayActions::setLibraryFilterVisible,
                            ),
                            onFiltersApply = libraryActions.onFiltersApply,
                        ),
                        navigation = NavigationContentInput(
                            actions = navigationActions,
                        ),
                        watch = WatchContentInput(
                            actions = AppDestinationWatchActions(
                                onRetry = watchRetryActions.onRetry,
                                onLoadMore = { watchPresenter.update(WatchSourcesScreenState::showAllWatchSources) },
                                onSourceClick = { source ->
                                    watchFlowNavigationActions.openEpisodes(
                                        source = source,
                                        animeId = watchAnime?.id,
                                        downloadMode = activeDownloadMode,
                                    )
                                },
                                onEpisodeClick = playbackRequestCoordinator::request,
                                onResumePlayback = { progress ->
                                    playbackRequestCoordinator.requestResume(progress)
                                },
                            ),
                            state = AppDestinationContentState(
                                selectedAnime = detailsAnime ?: detailsNavState.selectedAnime,
                                watchAnime = watchAnime,
                                selectedWatchSource = selectedWatchSource,
                                playbackError = playerState.errorMessage,
                                playbackLoading = playerState.isLoading,
                                watchRepositoryAvailable = watchRepository != null,
                                detailsError = detailsNavState.detailsError,
                                detailsResumeState = detailsResumeState,
                                isPlayerRoute = routePresentation.isPlayerRoute,
                                playbackHostAvailable = playbackCallbacks.playbackHost != null,
                                currentRoute = routePresentation.currentRoute,
                            ),
                            playbackContext = AppDestinationPlaybackContext(
                                episodeDownloadRepository = episodeDownloadRepository,
                                offlineWatchDataRepository = offlineWatchDataRepository,
                                offlineTitleMetadataRepository = offlineTitleMetadataRepository,
                                resumeFrameContent = platformCallbacks.resumeFrameContent,
                                downloadMode = activeDownloadMode,
                                watchPresenter = watchPresenter,
                                episodesPresenter = episodesPresenter,
                            ),
                        ),
                        platform = PlatformContentInput(
                            dataContext = AppDestinationDataContext(
                                libraryRepository = libraryRepository,
                                profileRepository = profileRepository,
                                languageMode = appSettingsState.languageMode,
                            ),
                            hostContext = AppDestinationHostContext(
                                systemLanguage = systemLanguage,
                                includeNavigationBarPadding = true,
                                onLibraryChanged = refreshLocalData,
                                modifier = Modifier.fillMaxSize(),
                            ),
                        ),
                        settings = SettingsContentInput(
                            actions = SettingsScreenActions(
                                onLanguageModeChange = settingsActions.onLanguageModeChange,
                                onThemeChange = settingsActions.onThemeChange,
                                onThemeModeChange = settingsActions.onThemeModeChange,
                                onSystemColorSchemeChange = settingsActions.onSystemColorSchemeChange,
                                onAmoledChange = settingsActions.onAmoledChange,
                                onAutoSkipChange = settingsActions.onAutoSkipChange,
                                onNotificationsClick = platformCallbacks.onConfigureNotifications,
                                onDiscordClick = discordSettingsActions.onClick,
                                onDiscordChange = discordSettingsActions.onChange,
                                onCheckForUpdates = platformCallbacks.onCheckForUpdates,
                                onExportLogs = platformCallbacks.onExportLogs,
                                onBackClick = navigationActions.onSettingsBack,
                            ),
                            state = SettingsScreenState(
                                languageMode = appSettingsState.languageMode,
                                darkTheme = appSettingsState.darkTheme,
                                themeMode = appSettingsState.themeMode,
                                versionName = appVersionName,
                                useSystemColorScheme = appSettingsState.useSystemColorScheme,
                                useAmoledTheme = appSettingsState.useAmoledTheme,
                                autoSkipSegments = appSettingsState.autoSkipSegments,
                                notificationPermissionState = appSettingsState.notificationPermissionState,
                                showBackButton = true,
                                discordEnabled = platformCallbacks.discordRpcController?.isEnabled() == true,
                                discordAvailable = platformCallbacks.discordRpcController != null,
                                notificationsAvailable = platformCallbacks.notificationsAvailable,
                            ),
                            listsState = AppDestinationSettingsListsState(
                                settings = settingsListState,
                            ),
                        ),
                        profile = ProfileContentInput(
                            profilePresenter = profilePresenter,
                            avatarEditAvailable = platformCallbacks.profileAvatarEditAvailable,
                            onAvatarEdit = platformCallbacks.onProfileAvatarEdit,
                        ),
                        sources = SourcesContentInput(
                            state = AppDestinationSourceState(
                                sources = sourceCallbacks.sources,
                                selectedSourceId = appSettingsState.currentSelectedSourceId,
                            ),
                            externalSourcesState = AppDestinationExternalSourcesState(
                                controller = sourceCallbacks.externalSourceRepositoryController,
                                selectedTab = selectedSourcesTab,
                                onSelectedTabChange = { selectedSourcesTab = it },
                            ),
                            sourceConfigContent = sourceCallbacks.sourceConfigContent,
                        ),
                    )
                    if (!enableOnboarding || appSettingsState.onboardingCompleted) {
                    AppProductionRoot(

                        currentDestination = topLevelDestination,
                            onNavigationEvent = { event ->
                                if (event is AppNavigationEvent.SelectTopLevel) {
                                    rootNavigationCoordinator.select(event.destination.toAppDestination())
                                }
                            },
                        showBottomBar = appBottomBarVisible(
                            selectedTab = selectedTab,
                            currentRoute = routePresentation.currentRoute,
                        ),
                        includeNavigationBarPadding = true,
                        transitionDirection = navigationState.transitionDirection,
                        // Settings is visually an overlay on top of the Profile tab, not a
                        // distinct destination -- report the underlying Profile route here so
                        // the root AnimatedContent treats Profile<->Settings as one continuous
                        // slot (no remount, no crossfade) instead of tearing down and
                        // rebuilding both screens on every visit. AppDestinationContent still
                        // knows Settings is actually open via the live selectedTab check below
                        // (settingsVisible), independent of this value.
                        contentRoute = if (routePresentation.currentRoute is AppRoute.Settings) {
                            AppRoute.TopLevel(org.akkirrai.hibiki.app.navigation.AppTopLevelDestination.PROFILE)
                        } else if (routePresentation.currentRoute.isWatchFlowRoute()) {
                            // Sources -> episodes -> player for the same anime is one continuous
                            // flow: report a route stable across those three (keyed only on the
                            // anime id) so AppRootContentState stays equal across them and the root
                            // AnimatedContent treats it as one slot -- same trick as Settings/Profile
                            // above. AppDestinationWatchRoute doesn't read this route back for its
                            // own sources/episodes/player branching (it uses the live
                            // selectedWatchSource/isPlayerRoute state instead), so this substitution
                            // is safe; it only needs to keep satisfying isWatchRouteDriven().
                            AppRoute.WatchSources(watchAnime?.id.orEmpty(), activeDownloadMode)
                        } else {
                            routePresentation.currentRoute
                        },
                        contentTransitionKey = if (
                            routePresentation.currentRoute is AppRoute.TopLevel ||
                            routePresentation.currentRoute is AppRoute.Details ||
                            routePresentation.currentRoute is AppRoute.Settings
                        ) {
                            org.akkirrai.hibiki.app.navigation.AppTransitionKey(
                                route = "top-level-content",
                                identity = if (selectedTab == AppDestination.SETTINGS) {
                                    "${org.akkirrai.hibiki.app.navigation.AppTopLevelDestination.PROFILE.name}:PROFILE"
                                } else {
                                    "${topLevelDestination.name}:${selectedTab.name}"
                                },
                            )
                        } else if (routePresentation.currentRoute.isWatchFlowRoute()) {
                            // Sources -> episodes -> player for the same anime is one continuous
                            // flow, not a sequence of separate screens -- keying this on just the
                            // anime id (like Details keys on animeId, not the full route) means
                            // picking a voiceover or opening an episode recomposes in place instead
                            // of cross-fading the whole screen on every step. A different anime
                            // still gets its own fade, same as Details does.
                            org.akkirrai.hibiki.app.navigation.AppTransitionKey(
                                route = "watch-flow",
                                identity = "${topLevelDestination.name}:${selectedTab.name}:${watchAnime?.id.orEmpty()}",
                            )
                        } else {
                            appShellTransitionKey(
                                topLevelDestination = topLevelDestination,
                                selectedTab = selectedTab.name,
                                detailsId = detailsNavState.selectedAnime?.id,
                                watchId = watchAnime?.id,
                                sourceId = selectedWatchSource?.sourceId,
                                routeKey = routePresentation.currentTransitionKey,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        tabContent = { tab ->
                            val appDestination = tab.toAppDestination()
                            AppDestinationTabContent(input = destinationInputFor(appDestination), tab = appDestination)
                        },
                    ) { animatedDestination, animatedRoute ->
                        val animatedTab = animatedDestination.toAppDestination(
                            settingsVisible = selectedTab == AppDestination.SETTINGS,
                        )
                        AppDestinationContent(
                            routeOverride = animatedRoute,
                            input = destinationInputFor(animatedTab),
                        )
                    }
                    }
                    HibikiAppShellOverlayLayer(
                        playbackHost = playbackCallbacks.playbackHost,
                        currentRoute = routePresentation.currentRoute,
                        activePlaybackRoute = activePlaybackRoute,
                        pendingPlaybackContext = pendingPlaybackContext,
                        navigationState = navigationState,
                        playbackLoading = playerState.isLoading,
                        playbackError = playerState.errorMessage,
                        onRetryPlayback = { playbackOverlayActions.retry(playerState.lastPlaybackRequest) },
                        onDismissPlayback = playbackOverlayActions::dismiss,
                        onEpisodeSelected = { episode, selectionContext ->
                            playbackRequestCoordinator.request(
                                episode = episode,
                                episodesOverride = selectionContext.episodes,
                                fallbackSource = WatchSource(
                                    sourceId = selectionContext.sourceId,
                                    title = selectionContext.sourceTitle,
                                    episodeCount = selectionContext.episodes.size,
                                    qualityLabel = selectionContext.selectedQualityLabel,
                                ),
                                replacePlayerRoute = true,
                            )
                        },
                        onPlaybackSettingsAction = playbackRequestCoordinator::handleSettingsAction,
                        onPlaybackOverlayEvent = { event -> navigationState = navigationState.reduce(event) },
                        enableOnboarding = enableOnboarding,
                        onboardingCompleted = appSettingsState.onboardingCompleted,
                        sources = sourceCallbacks.sources,
                        onboardingSourceId = appSettingsState.onboardingSourceId,
                        systemLanguage = systemLanguage,
                        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
                        onRequestOnboardingNotificationPermission = platformCallbacks.onRequestOnboardingNotificationPermission,
                        onOnboardingComplete = { sourceId ->
                            appSettingsState.completeOnboarding(sourceId)
                            sourceSelectionCoordinator.select(sourceId)
                        },
                        discordAuthDialogOpen = isDiscordAuthDialogOpen,
                        discordRpcController = platformCallbacks.discordRpcController,
                        discordRpcState = discordRpcState,
                        pendingDiscordToken = discordAuthState.pendingToken,
                        onPendingDiscordTokenChange = { token ->
                            discordAuthState.pendingToken = token
                            if (token != null) openDiscordAuthDialog()
                        },
                        onDiscordBrowserSignIn = platformCallbacks.onDiscordBrowserSignIn,
                        onCloseDiscordAuth = ::closeDiscordAuthDialog,
                    )
                }
            }
    }
}
}
