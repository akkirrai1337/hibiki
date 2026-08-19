package org.akkirrai.hibiki.app.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.akkirrai.hibiki.app.shell.player.watch.HibikiWatchFlowNavigationActions
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.app.navigation.AppDestination
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppNavigationState
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.app.navigation.AppTransitionKey
import org.akkirrai.hibiki.app.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.app.navigation.currentRoute
import org.akkirrai.hibiki.app.navigation.currentTransitionKey
import org.akkirrai.hibiki.app.navigation.navigateBackFromDetails
import org.akkirrai.hibiki.app.navigation.navigateToDetails
import org.akkirrai.hibiki.app.navigation.navigateToSettings
import org.akkirrai.hibiki.app.navigation.navigateToSourcePackageInfo
import org.akkirrai.hibiki.app.navigation.navigateToSourceRepositories
import org.akkirrai.hibiki.app.navigation.reduce
import org.akkirrai.hibiki.app.navigation.reduceHibikiRootTabSelection
import org.akkirrai.hibiki.app.navigation.resolveWatchFlowBackEffect
import org.akkirrai.hibiki.app.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.app.navigation.selectedAppDestination
import org.akkirrai.hibiki.app.navigation.toTopLevelDestination
import org.akkirrai.hibiki.player.EpisodesPresenter
import org.akkirrai.hibiki.player.EpisodesScreenState
import org.akkirrai.hibiki.player.HibikiPlaybackSession

internal fun shouldApplyTopSystemInset(destination: AppDestination): Boolean =
    destination != AppDestination.SETTINGS

internal fun AppRoute?.downloadModeEnabled(): Boolean = when (this) {
    is AppRoute.WatchSources -> downloadMode
    is AppRoute.Episodes -> downloadMode
    else -> false
}

@Stable
internal class HibikiAppShellNavigationState {
    val route: MutableState<AppNavigationState> = mutableStateOf(AppNavigationState())
    val libraryFilterOverlay: AppOverlay = AppOverlay.Sheet("library-filter")
}

@Composable
internal fun rememberHibikiAppShellNavigationState(): HibikiAppShellNavigationState =
    remember { HibikiAppShellNavigationState() }

internal data class HibikiShellRoutePresentation(
    val selectedTab: AppDestination,
    val topLevelDestination: AppTopLevelDestination,
    val currentRoute: AppRoute,
    val currentTransitionKey: AppTransitionKey,
    val activeDownloadMode: Boolean,
) {
    val isPlayerRoute: Boolean
        get() = currentRoute is AppRoute.Player
}

internal fun AppNavigationState.toHibikiShellRoutePresentation(): HibikiShellRoutePresentation {
    val selectedTab = selectedAppDestination()
    return HibikiShellRoutePresentation(
        selectedTab = selectedTab,
        topLevelDestination = selectedTab.toTopLevelDestination(),
        currentRoute = currentRoute,
        currentTransitionKey = currentTransitionKey,
        activeDownloadMode = currentRoute.downloadModeEnabled(),
    )
}

internal class HibikiRootNavigationCoordinator(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val selectedTab: () -> AppDestination,
    private val presenter: AnimeCatalogPresenter,
    private val setDetailsAnime: (Anime?) -> Unit,
    private val playbackSession: HibikiPlaybackSession,
    private val episodesPresenter: EpisodesPresenter,
    private val resetPlayerState: () -> Unit,
) {
    fun select(destination: AppDestination) {
        val currentState = navigationState()
        val result = reduceHibikiRootTabSelection(
            state = currentState,
            selectedTab = selectedTab(),
            destination = destination,
        )
        if (!result.handled) return
        setNavigationState(result.state)

        // A regular top-level switch has no details/watch state to clear. Avoid
        // emitting several identical presenter states and invalidating the
        // whole shell before the screen transition can start.
        val hasNestedState = currentState.currentRoute !is AppRoute.TopLevel ||
            presenter.state.value.selectedAnime != null
        if (!hasNestedState) return

        presenter.clearDetails()
        setDetailsAnime(null)
        playbackSession.teardown()
        episodesPresenter.setState(EpisodesScreenState())
        resetPlayerState()
    }
}

internal fun createHibikiRootNavigationCoordinator(
    navigationState: () -> AppNavigationState,
    setNavigationState: (AppNavigationState) -> Unit,
    selectedTab: () -> AppDestination,
    presenter: AnimeCatalogPresenter,
    setDetailsAnime: (Anime?) -> Unit,
    playbackSession: HibikiPlaybackSession,
    episodesPresenter: EpisodesPresenter,
    resetPlayerState: () -> Unit,
): HibikiRootNavigationCoordinator = HibikiRootNavigationCoordinator(
    navigationState = navigationState,
    setNavigationState = setNavigationState,
    selectedTab = selectedTab,
    presenter = presenter,
    setDetailsAnime = setDetailsAnime,
    playbackSession = playbackSession,
    episodesPresenter = episodesPresenter,
    resetPlayerState = resetPlayerState,
)

internal class HibikiDetailsNavigationActions(
    private val presenter: AnimeCatalogPresenter,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val setDetailsAnime: (Anime?) -> Unit,
) {
    fun open(anime: Anime) {
        presenter.openDetails(anime)
        setDetailsAnime(anime)
        val currentRoute = navigationState().currentRoute
        if (currentRoute !is AppRoute.Details || currentRoute.animeId != anime.id) {
            setNavigationState(navigationState().navigateToDetails(anime.id))
        }
    }

    fun close() {
        val nextNavigationState = navigationState().navigateBackFromDetails()
        setNavigationState(nextNavigationState)
        presenter.closeDetails()
        setDetailsAnime(null)
    }
}

internal enum class HibikiBackCleanup {
    None,
    ActivePlayback,
    Player,
    Episodes,
    Details,
}

internal data class HibikiSystemBackResult(
    val state: AppNavigationState,
    val handled: Boolean,
    val cleanup: HibikiBackCleanup = HibikiBackCleanup.None,
    val clearPlaybackReturnRoute: Boolean = false,
    val effect: WatchFlowBackEffect = WatchFlowBackEffect.None,
)

internal fun reduceHibikiSystemBack(
    navigationState: AppNavigationState,
    selectedTab: AppDestination,
    hasActivePlayback: Boolean,
    playbackReturnRoute: AppRoute?,
): HibikiSystemBackResult {
    val routeBeforeBack = navigationState.currentRoute
    if (navigationState.overlays.isNotEmpty()) {
        return HibikiSystemBackResult(navigationState.reduce(AppNavigationEvent.Back), handled = true)
    }
    if (selectedTab == AppDestination.SETTINGS) {
        val canPopSettings = routeBeforeBack is AppRoute.Settings
        return HibikiSystemBackResult(
            state = if (canPopSettings) navigationState.reduce(AppNavigationEvent.Back) else navigationState,
            handled = true,
        )
    }
    if (hasActivePlayback) {
        val transition = resolveWatchFlowBackTransition(navigationState, playbackReturnRoute)
        return HibikiSystemBackResult(
            state = transition.state,
            handled = true,
            cleanup = HibikiBackCleanup.ActivePlayback,
            clearPlaybackReturnRoute = true,
            effect = transition.effect,
        )
    }
    if (navigationState.backStack.isEmpty()) return HibikiSystemBackResult(navigationState, handled = false)
    val transition = resolveWatchFlowBackTransition(navigationState, playbackReturnRoute)
    val cleanup = when (routeBeforeBack) {
        is AppRoute.Player -> HibikiBackCleanup.Player
        is AppRoute.Episodes, is AppRoute.WatchSources -> HibikiBackCleanup.Episodes
        is AppRoute.Details -> if (
            resolveWatchFlowBackEffect(routeBeforeBack, transition.state.currentRoute) == WatchFlowBackEffect.CloseDetails
        ) HibikiBackCleanup.Details else HibikiBackCleanup.None
        else -> HibikiBackCleanup.None
    }
    return HibikiSystemBackResult(
        state = transition.state,
        handled = true,
        cleanup = cleanup,
        clearPlaybackReturnRoute = routeBeforeBack is AppRoute.Player,
        effect = transition.effect,
    )
}

internal class HibikiSystemBackCoordinator(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val selectedTab: () -> AppDestination,
    private val playbackSession: HibikiPlaybackSession,
    private val hasActivePlayback: () -> Boolean,
    private val playbackReturnRoute: () -> AppRoute?,
    private val setPlaybackReturnRoute: (AppRoute?) -> Unit,
    private val applyWatchFlowBackEffect: (WatchFlowBackEffect) -> Unit,
    private val closeDetails: () -> Unit,
) {
    fun handle() {
        val result = reduceHibikiSystemBack(
            navigationState = navigationState(),
            selectedTab = selectedTab(),
            hasActivePlayback = hasActivePlayback(),
            playbackReturnRoute = playbackReturnRoute(),
        )
        if (!result.handled) return
        setNavigationState(result.state)
        if (result.cleanup == HibikiBackCleanup.Details) closeDetails()
        if (result.clearPlaybackReturnRoute) setPlaybackReturnRoute(null)
        when (result.cleanup) {
            HibikiBackCleanup.ActivePlayback,
            HibikiBackCleanup.Episodes,
            -> {
                playbackSession.teardown()
                applyWatchFlowBackEffect(result.effect)
            }
            HibikiBackCleanup.Player -> {
                playbackSession.cancelJob()
                playbackSession.clearRouteState()
                applyWatchFlowBackEffect(result.effect)
            }
            HibikiBackCleanup.Details -> Unit
            HibikiBackCleanup.None -> Unit
        }
    }
}

internal fun createHibikiSystemBackCoordinator(
    navigationState: () -> AppNavigationState,
    setNavigationState: (AppNavigationState) -> Unit,
    selectedTab: () -> AppDestination,
    playbackSession: HibikiPlaybackSession,
    hasActivePlayback: () -> Boolean,
    playbackReturnRoute: () -> AppRoute?,
    setPlaybackReturnRoute: (AppRoute?) -> Unit,
    applyWatchFlowBackEffect: (WatchFlowBackEffect) -> Unit,
    closeDetails: () -> Unit,
): HibikiSystemBackCoordinator = HibikiSystemBackCoordinator(
    navigationState = navigationState,
    setNavigationState = setNavigationState,
    selectedTab = selectedTab,
    playbackSession = playbackSession,
    hasActivePlayback = hasActivePlayback,
    playbackReturnRoute = playbackReturnRoute,
    setPlaybackReturnRoute = setPlaybackReturnRoute,
    applyWatchFlowBackEffect = applyWatchFlowBackEffect,
    closeDetails = closeDetails,
)

internal data class AppDestinationNavigationActions(
    val onAnimeClick: (Anime) -> Unit,
    val onBackFromDetails: () -> Unit,
    val onWatchClick: (Anime) -> Unit,
    val onBackFromWatch: () -> Unit,
    val onProfileSettingsClick: () -> Unit,
    val onSourceSelected: (String) -> Unit,
    val onBrowseCatalog: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onSourceRepositoriesClick: () -> Unit,
    val onSourcePackageInfoClick: (String, String) -> Unit,
    val onSettingsBack: () -> Unit,
)

internal fun createHibikiAppShellDestinationNavigationActions(
    detailsNavigationActions: HibikiDetailsNavigationActions,
    watchFlowNavigationActions: HibikiWatchFlowNavigationActions,
    rootNavigationCoordinator: HibikiRootNavigationCoordinator,
    activeDownloadMode: Boolean,
    updateNavigationState: ((AppNavigationState) -> AppNavigationState) -> Unit,
    onSourceSelected: (String) -> Unit,
): AppDestinationNavigationActions = AppDestinationNavigationActions(
    onAnimeClick = detailsNavigationActions::open,
    onBackFromDetails = detailsNavigationActions::close,
    onWatchClick = { anime ->
        watchFlowNavigationActions.openWatch(anime, activeDownloadMode)
    },
    onBackFromWatch = watchFlowNavigationActions::backFromWatch,
    onProfileSettingsClick = {
        updateNavigationState { it.navigateToSettings() }
    },
    onSourceSelected = onSourceSelected,
    onBrowseCatalog = { rootNavigationCoordinator.select(AppDestination.CATALOG) },
    onOpenLibrary = { rootNavigationCoordinator.select(AppDestination.LIBRARY) },
    onSourceRepositoriesClick = {
        updateNavigationState { it.navigateToSourceRepositories() }
    },
    onSourcePackageInfoClick = { repositoryUrl, sourceId ->
        updateNavigationState { it.navigateToSourcePackageInfo(repositoryUrl, sourceId) }
    },
    onSettingsBack = {
        updateNavigationState { it.reduce(AppNavigationEvent.Back) }
    },
)
