
package org.akkirrai.hibiki.shared.app.shell

import org.akkirrai.hibiki.shared.player.HibikiPlaybackSession
import org.akkirrai.hibiki.shared.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.shared.home.state.launchHomeDescriptionEnrichment
import org.akkirrai.hibiki.shared.app.launchLocalDataRefresh
import org.akkirrai.hibiki.shared.app.destination.content.AppDestinationContent
import org.akkirrai.hibiki.shared.app.destination.context.*
import org.akkirrai.hibiki.shared.app.destination.actions.*
import org.akkirrai.hibiki.shared.app.destination.state.*
import org.akkirrai.hibiki.shared.app.screen.AppPlatformCallbacks
import org.akkirrai.hibiki.shared.source.AppSourcePlatformCallbacks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.shared.app.shell.layout.AppProductionRoot
import org.akkirrai.hibiki.shared.app.shell.navigation.*
import org.akkirrai.hibiki.shared.app.shell.settings.*
import org.akkirrai.hibiki.shared.app.shell.profile.*
import org.akkirrai.hibiki.shared.app.shell.home.*
import org.akkirrai.hibiki.shared.app.shell.catalog.*
import org.akkirrai.hibiki.shared.app.shell.player.*
import org.akkirrai.hibiki.shared.app.shell.player.watch.*
import org.akkirrai.hibiki.shared.app.shell.layout.*
import org.akkirrai.hibiki.shared.app.shell.runtime.*
import org.akkirrai.hibiki.shared.app.shell.library.*
import org.akkirrai.hibiki.shared.app.shell.overlay.*
import org.akkirrai.hibiki.shared.app.shell.source.*
import org.akkirrai.hibiki.shared.app.shell.overlay.HibikiAppShellOverlayLayer
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.player.model.TitleWatchState
import org.akkirrai.hibiki.shared.library.presentation.LibraryPresenter
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.home.state.preserveLoadedDescriptions as preserveHomeDescriptions
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfilePresenter
import org.akkirrai.hibiki.shared.layout.appRootTopInsetPadding
import org.akkirrai.hibiki.shared.layout.AppLayoutOptions
import org.akkirrai.hibiki.shared.settings.ThemeMode
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.settings.NotificationPermissionState
import org.akkirrai.hibiki.shared.settings.DiscordRpcUiState
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.activeOverlay
import org.akkirrai.hibiki.shared.navigation.appBackHandlerEnabled
import org.akkirrai.hibiki.shared.navigation.appBottomBarVisible
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.appShellTransitionKey
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.currentTransitionKey
import org.akkirrai.hibiki.shared.navigation.selectedWatchSource
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.navigation.selectedAppDestination
import org.akkirrai.hibiki.shared.navigation.toTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.toAppDestination
import org.akkirrai.hibiki.shared.player.PlayerPresenter
import org.akkirrai.hibiki.shared.player.PlayerUiState
import org.akkirrai.hibiki.shared.player.AppPlaybackPlatformCallbacks
import org.akkirrai.hibiki.shared.player.EpisodesUiState
import org.akkirrai.hibiki.shared.player.WatchDataRepository
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository

import org.akkirrai.hibiki.shared.player.WatchSourcesPresenter
import org.akkirrai.hibiki.shared.player.shouldShowPlaybackHost
import org.akkirrai.hibiki.shared.navigation.navigateToEpisodes
import org.akkirrai.hibiki.shared.navigation.navigateToSettings
import org.akkirrai.hibiki.shared.navigation.navigateToPlayer
import org.akkirrai.hibiki.shared.navigation.navigateToWatchSources
import org.akkirrai.hibiki.shared.navigation.reduceDetailsOverlayChange
import org.akkirrai.hibiki.shared.navigation.reduceOverlayVisibilityChange
import org.akkirrai.hibiki.shared.player.WatchSourcesScreenState
import org.akkirrai.hibiki.shared.player.errorEpisodesState
import org.akkirrai.hibiki.shared.player.initialEpisodesState
import org.akkirrai.hibiki.shared.player.initialWatchSourcesState
import org.akkirrai.hibiki.shared.player.loadedEpisodesState
import org.akkirrai.hibiki.shared.player.beginPlaybackLoad
import org.akkirrai.hibiki.shared.player.withPlaybackError
import org.akkirrai.hibiki.shared.player.resetForNavigation
import org.akkirrai.hibiki.shared.player.withPlaybackLoaded
import org.akkirrai.hibiki.shared.player.withLoadedSources
import org.akkirrai.hibiki.shared.player.withWatchSourcesError
import org.akkirrai.hibiki.shared.player.resolveResumeWatchState
import org.akkirrai.hibiki.shared.player.showAllWatchSources
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler

@Composable
internal fun HibikiAppShell(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
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
    platformCallbacks: AppPlatformCallbacks = AppPlatformCallbacks(),
    sourceCallbacks: AppSourcePlatformCallbacks = AppSourcePlatformCallbacks(),
    watchRepository: WatchDataRepository? = null,
    playbackCallbacks: AppPlaybackPlatformCallbacks = AppPlaybackPlatformCallbacks(),
    layoutOptions: AppLayoutOptions = AppLayoutOptions(),
    catalogRefreshKey: Any? = null,
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
    val state by presenter.state.collectAsState()
    val homeSearchPresenter = resources.homeSearchPresenter
    val homeSearchActions = HibikiHomeSearchActions(homeSearchPresenter)
    val homeSearchState by homeSearchPresenter.state.collectAsState()
    val homePresenter = resources.homePresenter
    val homeState by homePresenter.state.collectAsState()

    fun setHomeStatePreservingDescriptions(state: HomeUiState) {
        homePresenter.setState(state.preserveHomeDescriptions(homePresenter.state.value))
    }
    val homeListState = resources.homeListState
    val catalogListState = resources.catalogListState
    val settingsListState = resources.settingsListState
    val sourceSearchPresenter = resources.sourceSearchPresenter
    val sourceSearchActions = HibikiSourceSearchActions(sourceSearchPresenter)
    val sourceSearchState by sourceSearchPresenter.state.collectAsState()
    val externalSourceRepositoryState = sourceCallbacks.externalSourceRepositoryController?.state?.collectAsState()?.value
    val watchPresenter = resources.watchPresenter
    val watchState by watchPresenter.state.collectAsState()
    val watchFlowState = rememberHibikiWatchFlowState()
    var detailsAnime by watchFlowState::detailsAnime
    val watchAnime = detailsAnime ?: state.selectedAnime
    var detailsResumeState by watchFlowState::detailsResumeState
    var watchLoadGeneration by watchFlowState::watchLoadGeneration
    var forceWatchSourcesRefresh by watchFlowState::forceWatchSourcesRefresh
    var episodesLoadGeneration by watchFlowState::episodesLoadGeneration
    val episodesPresenter = resources.episodesPresenter
    val episodesState by episodesPresenter.state.collectAsState()
    val shellNavigationState = rememberHibikiAppShellNavigationState()
    var navigationState by shellNavigationState.route
    val libraryFilterOverlay = shellNavigationState.libraryFilterOverlay
    val isLibraryFilterOverlayOpen = navigationState.activeOverlay == libraryFilterOverlay
    val selectedWatchSource = navigationState.selectedWatchSource
    val playerPresenter = resources.playerPresenter
    val playerState by playerPresenter.state.collectAsState()
    val playbackSession = remember { HibikiPlaybackSession() }
    var selectedSourcesTab by rememberSaveable { mutableStateOf(0) }
    var playbackJob by playbackSession.job
    var homeRefreshJob by remember { mutableStateOf<Job?>(null) }
    var activePlaybackRoute by playbackSession.activeRoute
    var pendingPlaybackContext by playbackSession.pendingContext
    var playbackReturnRoute by playbackSession.returnRoute
    val libraryPresenter = resources.libraryPresenter
    val libraryActions = HibikiLibraryActions(libraryPresenter)
    val libraryState by libraryPresenter.state.collectAsState()
    val profilePresenter = resources.profilePresenter
    val profileState by profilePresenter.state.collectAsState()
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
    LaunchedEffect(selectedTab, sourceCallbacks.externalSourceRepositoryController) {
        if (selectedTab == AppDestination.SOURCES) {
            sourceCallbacks.externalSourceRepositoryController?.refreshRepositories()
        }
    }
    val appSettingsState = rememberHibikiAppSettingsState(
        settingsStore = settingsStore,
        selectedSourceId = sourceCallbacks.selectedSourceId,
        onboardingNotificationPermissionState = onboardingNotificationPermissionState,
    )
    val profileEditingState = rememberHibikiProfileEditingState(profileState.data.profileName)
    val profileActions = HibikiProfileActions(
        repository = profileRepository,
        presenter = profilePresenter,
        setEditing = { profileEditingState.isEditing = it },
        getEditedName = { profileEditingState.editedName },
        setEditedName = { profileEditingState.editedName = it },
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
        onDisposed = sourceSelectionCoordinator::cancel,
    )

    HibikiAppDataEffects(
        libraryRepository = libraryRepository,
        libraryPresenter = libraryPresenter,
        selectedAnimeKey = state.selectedAnime?.id,
        homeRepository = homeRepository,
        homePresenter = homePresenter,
        setHomeState = ::setHomeStatePreservingDescriptions,
        profileRepository = profileRepository,
        profilePresenter = profilePresenter,
    )

    HibikiWatchDataEffects(
        selectedAnime = state.selectedAnime,
        detailsLoading = state.isDetailsLoading,
        progressRepository = progressRepository,
        offlineTitleMetadataRepository = offlineTitleMetadataRepository,
        onDetailsAnimeChanged = { detailsAnime = it },
        onDetailsResumeStateChanged = { detailsResumeState = it },
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
    )
    val homeActions = createHibikiHomeActions(
        repository = homeRepository,
        presenter = homePresenter,
        requests = homeDescriptionRequests,
        scope = scope,
        setHomeState = ::setHomeStatePreservingDescriptions,
    )

    val playbackEffects = HibikiAppShellPlaybackEffects(
        episodesPresenter = episodesPresenter,
        playerPresenter = playerPresenter,
        invalidateEpisodes = { episodesLoadGeneration++ },
    )

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
        episodesState = { episodesState },
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
        setAutoSkipSegments = { appSettingsState.autoSkipSegments = it },
        setAutoPlayNextEpisode = { appSettingsState.autoPlayNextEpisode = it },
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

    val detailsNavigationActions = HibikiDetailsNavigationActions(
        presenter = presenter,
        navigationState = { navigationState },
        setNavigationState = { navigationState = it },
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

    CompositionLocalProvider(
        LocalAppTextResolver provides DefaultAppTextResolver(appSettingsState.languageMode, systemLanguage),
    ) {
        MaterialTheme(
            colorScheme = hibikiAppColorScheme(appSettingsState.themeMode, appSettingsState.useAmoledTheme),
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                playbackCallbacks.playerWindowMode(routePresentation.isPlayerRoute)
                AppSystemBackHandler(
                    enabled = appBackHandlerEnabled(
                        state = navigationState,
                    ),
                    onBack = systemBackCoordinator::handle,
                ) {
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
                        includeNavigationBarPadding = layoutOptions.includeNavigationBarPadding,
                        transitionDirection = navigationState.transitionDirection,
                        contentRoute = routePresentation.currentRoute,
                        contentTransitionKey = if (
                            routePresentation.currentRoute is AppRoute.TopLevel ||
                            routePresentation.currentRoute is AppRoute.Details ||
                            routePresentation.currentRoute is AppRoute.Settings
                        ) {
                            org.akkirrai.hibiki.shared.navigation.AppTransitionKey(
                                route = "top-level-content",
                                identity = if (selectedTab == AppDestination.SETTINGS) {
                                    "${org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination.PROFILE.name}:PROFILE"
                                } else {
                                    "${topLevelDestination.name}:${selectedTab.name}"
                                },
                            )
                        } else {
                            appShellTransitionKey(
                                topLevelDestination = topLevelDestination,
                                selectedTab = selectedTab.name,
                                detailsId = state.selectedAnime?.id,
                                watchId = watchAnime?.id,
                                sourceId = selectedWatchSource?.sourceId,
                                routeKey = routePresentation.currentTransitionKey,
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .appRootTopInsetPadding(layoutOptions.applyStatusBarPadding),
                    ) { animatedDestination, animatedRoute ->
                        val animatedTab = animatedDestination.toAppDestination(
                            settingsVisible = selectedTab == AppDestination.SETTINGS,
                        )
                        AppDestinationContent(
                            routeOverride = animatedRoute,
                            input = AppDestinationContentInput(
                            selectedTab = animatedTab,
                            catalog = CatalogContentInput(
                                actions = AppDestinationCatalogActions(
                                    onQueryChange = catalogActions.onQueryChange,
                                    onFiltersChange = catalogActions.onFiltersChange,
                                    onRetry = catalogActions.onRetry,
                                    onRefresh = catalogActions.onRefresh,
                                    onLoadMoreRetry = catalogActions.onLoadMoreRetry,
                                    onSortSelected = catalogActions.onSortSelected,
                                ),
                                state = AppDestinationCatalogState(
                                    query = state.query,
                                    items = state.items,
                                    filters = state.filters,
                                    filterCatalog = state.filterCatalog,
                                    ui = state,
                                    listState = catalogListState,
                                ),
                            ),
                            home = HomeContentInput(
                                state = AppDestinationHomeState(
                                    ui = homeState,
                                    search = homeSearchState,
                                    listState = homeListState,
                                ),
                                actions = AppDestinationHomeActions(
                                    onQueryChange = homeSearchActions.onQueryChange,
                                    onSearchClear = homeSearchActions.onClear,
                                    onFilterApply = homeSearchActions.onFilterApply,
                                    onSearchLoadMore = homeSearchActions.onLoadMore,
                                    onSearchRetry = homeSearchActions.onRetry,
                                    onItemVisible = homeActions.onItemVisible,
                                    onRefresh = homeActions.onRefresh,
                                ),
                            ),
                            library = LibraryContentInput(
                                state = AppDestinationLibraryState(
                                    entries = libraryState.visibleEntries,
                                    ui = libraryState,
                                    filterOverlayOpen = isLibraryFilterOverlayOpen,
                                ),
                                actions = AppDestinationLibraryActions(
                                    onCategorySelected = libraryActions.onCategorySelected,
                                    onSearchQueryChange = libraryActions.onSearchQueryChange,
                                    onSearchClear = libraryActions.onSearchClear,
                                    onFiltersApply = libraryActions.onFiltersApply,
                                    onFilterOpen = { overlayActions.setLibraryFilterVisible(true) },
                                    onFilterVisibilityChange = overlayActions::setLibraryFilterVisible,
                                ),
                            ),
                            navigation = NavigationContentInput(
                                actions = createHibikiAppShellDestinationNavigationActions(
                                    detailsNavigationActions = detailsNavigationActions,
                                    watchFlowNavigationActions = watchFlowNavigationActions,
                                    rootNavigationCoordinator = rootNavigationCoordinator,
                                    activeDownloadMode = activeDownloadMode,
                                    updateNavigationState = { reduce ->
                                        navigationState = reduce(navigationState)
                                    },
                                    onSourceSelected = sourceSelectionCoordinator::select,
                                ),
                                detailsOverlayState = AppDestinationDetailsOverlayState(
                                    posterPreviewOpen = navigationState.activeOverlay == AppOverlay.DetailsPosterPreview,
                                    onPosterPreviewOpenChange = overlayActions::setDetailsPosterPreviewOpen,
                                    titleSheetOpen = navigationState.activeOverlay == AppOverlay.DetailsTitleSheet,
                                    onTitleSheetOpenChange = overlayActions::setDetailsTitleSheetOpen,
                                    librarySheetOpen = navigationState.activeOverlay == AppOverlay.DetailsLibrarySheet,
                                    onLibrarySheetOpenChange = overlayActions::setDetailsLibrarySheetOpen,
                                ),
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
                                    selectedAnime = detailsAnime ?: state.selectedAnime,
                                    watchAnime = watchAnime,
                                    watchState = watchState,
                                    episodesState = episodesState,
                                    selectedWatchSource = selectedWatchSource,
                                    playbackError = playerState.errorMessage,
                                    playbackLoading = playerState.isLoading,
                                    watchRepositoryAvailable = watchRepository != null,
                                    isDetailsLoading = state.isDetailsLoading,
                                    detailsError = state.detailsError,
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
                                    includeNavigationBarPadding = layoutOptions.includeNavigationBarPadding,
                                    onLibraryChanged = refreshLocalData,
                                    onOpenUrl = platformCallbacks.onOpenUrl,
                                    onGitHubClick = platformCallbacks.onGitHubClick,
                                    modifier = Modifier.fillMaxSize(),
                                ),
                            ),
                            settings = SettingsContentInput(
                                actions = AppDestinationSettingsActions(
                                    onLanguageModeChange = settingsActions.onLanguageModeChange,
                                    onThemeChange = settingsActions.onThemeChange,
                                    onThemeModeChange = settingsActions.onThemeModeChange,
                                    onSystemColorSchemeChange = settingsActions.onSystemColorSchemeChange,
                                    onAmoledChange = settingsActions.onAmoledChange,
                                    onAutoSkipChange = settingsActions.onAutoSkipChange,
                                    onConfigureNotifications = platformCallbacks.onConfigureNotifications,
                                    onDiscordClick = discordSettingsActions.onClick,
                                    onDiscordChange = discordSettingsActions.onChange,
                                    onCheckForUpdates = platformCallbacks.onCheckForUpdates,
                                    onExportLogs = platformCallbacks.onExportLogs,
                                ),
                                state = AppDestinationSettingsState(
                                    darkTheme = appSettingsState.darkTheme,
                                    themeMode = appSettingsState.themeMode,
                                    appVersionName = appVersionName,
                                    useSystemColorScheme = appSettingsState.useSystemColorScheme,
                                    useAmoledTheme = appSettingsState.useAmoledTheme,
                                    autoSkipSegments = appSettingsState.autoSkipSegments,
                                    notificationPermissionState = appSettingsState.notificationPermissionState,
                                    showBackButton = layoutOptions.showSettingsBackButton,
                                    discordEnabled = platformCallbacks.discordRpcController?.isEnabled() == true,
                                    discordAvailable = platformCallbacks.discordRpcController != null,
                                    notificationsAvailable = platformCallbacks.notificationsAvailable,
                                ),
                                listsState = AppDestinationSettingsListsState(
                                    settings = settingsListState,
                                ),
                            ),
                            profile = ProfileContentInput(
                                state = AppDestinationProfileState(
                                    data = profileState.data,
                                    isEditing = profileEditingState.isEditing,
                                    editedName = profileEditingState.editedName,
                                    isLoading = profileState.isLoading,
                                    avatarEditAvailable = platformCallbacks.profileAvatarEditAvailable,
                                ),
                                actions = AppDestinationProfileActions(
                                    onNameChange = profileActions.onNameChange,
                                    onEditClick = profileActions.onEditClick,
                                    onSaveClick = profileActions.onSaveClick,
                                    onAvatarEdit = platformCallbacks.onProfileAvatarEdit,
                                    onAvatarPicked = profileActions.onAvatarPicked,
                                ),
                            ),
                            sources = SourcesContentInput(
                                state = AppDestinationSourceState(
                                    sources = sourceCallbacks.sources,
                                    selectedSourceId = appSettingsState.currentSelectedSourceId,
                                    search = sourceSearchState,
                                ),
                                searchActions = AppDestinationSourceSearchActions(
                                    onQueryChange = sourceSearchActions.onQueryChange,
                                    onClear = sourceSearchActions.onClear,
                                    onRetry = sourceSearchActions.onRetry,
                                    onRetryForSource = sourceSearchActions.onRetryForSource,
                                ),
                                externalSourcesState = AppDestinationExternalSourcesState(
                                    repository = externalSourceRepositoryState,
                                    controller = sourceCallbacks.externalSourceRepositoryController,
                                    selectedTab = selectedSourcesTab,
                                    onSelectedTabChange = { selectedSourcesTab = it },
                                    readClipboardText = sourceCallbacks.readClipboardText,
                                    copyText = sourceCallbacks.copyText,
                                ),
                            ),
                            ),
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
                        onEpisodeSelected = { episode ->
                            playbackRequestCoordinator.request(
                                episode = episode,
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
}
