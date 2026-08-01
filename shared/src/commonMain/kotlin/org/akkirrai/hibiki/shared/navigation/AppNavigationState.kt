package org.akkirrai.hibiki.shared.navigation

data class AppNavigationState(
    val currentTopLevel: AppTopLevelDestination = AppTopLevelDestination.HOME,
    val backStack: List<AppRoute> = emptyList(),
    val overlays: List<AppOverlay> = emptyList(),
    val playerSettingsDestination: AppPlayerSettingsDestination = AppPlayerSettingsDestination.Root,
    val transitionDirection: AppTransitionDirection = AppTransitionDirection.Forward,
)

val AppNavigationState.currentRoute: AppRoute
    get() = backStack.lastOrNull() ?: AppRoute.TopLevel(currentTopLevel)

val AppNavigationState.currentWatchRoute: AppRoute?
    get() = currentRoute.takeIf {
        it is AppRoute.WatchSources || it is AppRoute.Episodes || it is AppRoute.Player
    }

val AppNavigationState.activeOverlay: AppOverlay?
    get() = overlays.lastOrNull()

val AppNavigationState.currentTransitionKey: AppTransitionKey
    get() = currentRoute.transitionKey()

val AppNavigationState.selectedWatchSource: org.akkirrai.hibiki.shared.model.WatchSource?
    get() = backStack.asReversed()
        .mapNotNull { (it as? AppRoute.Episodes)?.source }
        .firstOrNull()

/** Resolves the visible root tab, including Settings nested under Profile. */
fun AppNavigationState.selectedAppDestination(): AppDestination = when {
    currentRoute is AppRoute.Settings -> AppDestination.SETTINGS
    else -> when (currentTopLevel) {
        AppTopLevelDestination.HOME -> AppDestination.HOME
        AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
        AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
        AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
        AppTopLevelDestination.PROFILE -> AppDestination.PROFILE
    }
}

/** Applies Android's existing push/pop behavior without depending on a platform navigator. */
fun AppNavigationState.reduce(event: AppNavigationEvent): AppNavigationState = when (event) {
    is AppNavigationEvent.SelectTopLevel -> copy(
        currentTopLevel = event.destination,
        backStack = emptyList(),
        overlays = emptyList(),
        playerSettingsDestination = AppPlayerSettingsDestination.Root,
        transitionDirection = AppTransitionDirection.Forward,
    )
    is AppNavigationEvent.Navigate -> when (val route = event.route) {
        is AppRoute.TopLevel -> reduce(AppNavigationEvent.SelectTopLevel(route.destination))
        else -> copy(
            backStack = backStack + route,
            transitionDirection = AppTransitionDirection.Forward,
        )
    }
    is AppNavigationEvent.Replace -> when (val route = event.route) {
        is AppRoute.TopLevel -> reduce(AppNavigationEvent.SelectTopLevel(route.destination))
        else -> copy(
            backStack = backStack.dropLast(1) + route,
            transitionDirection = AppTransitionDirection.Forward,
        )
    }
    is AppNavigationEvent.PresentOverlay -> {
        val playbackOverlay = event.overlay is AppOverlay.Playlist ||
            event.overlay is AppOverlay.PlayerSettings
        copy(
            overlays = if (playbackOverlay) {
                overlays.filterNot { it is AppOverlay.Playlist || it is AppOverlay.PlayerSettings } + event.overlay
            } else {
                overlays + event.overlay
            },
            playerSettingsDestination = when (event.overlay) {
                AppOverlay.Playlist,
                AppOverlay.PlayerSettings,
                -> AppPlayerSettingsDestination.Root
                else -> playerSettingsDestination
            },
        )
    }
    AppNavigationEvent.DismissOverlay -> copy(
        overlays = overlays.dropLast(1),
        transitionDirection = AppTransitionDirection.Pop,
        playerSettingsDestination = if (overlays.lastOrNull() is AppOverlay.PlayerSettings) {
            AppPlayerSettingsDestination.Root
        } else {
            playerSettingsDestination
        },
    )
    is AppNavigationEvent.SetPlayerSettingsDestination -> copy(
        playerSettingsDestination = event.destination,
    )
    AppNavigationEvent.OpenPlayerSettings -> copy(
        overlays = overlays.filterNot { it is AppOverlay.Playlist || it is AppOverlay.PlayerSettings } +
            AppOverlay.PlayerSettings,
        playerSettingsDestination = AppPlayerSettingsDestination.Root,
    )
    AppNavigationEvent.ClosePlayerSettings -> copy(
        overlays = if (overlays.lastOrNull() is AppOverlay.PlayerSettings) {
            overlays.dropLast(1)
        } else {
            overlays
        },
        playerSettingsDestination = AppPlayerSettingsDestination.Root,
    )
    AppNavigationEvent.Back -> when {
        overlays.lastOrNull() is AppOverlay.PlayerSettings &&
            playerSettingsDestination != AppPlayerSettingsDestination.Root -> copy(
                playerSettingsDestination = AppPlayerSettingsDestination.Root,
                transitionDirection = AppTransitionDirection.Pop,
            )
        overlays.isNotEmpty() -> reduce(AppNavigationEvent.DismissOverlay)
        backStack.isNotEmpty() -> copy(
            backStack = backStack.dropLast(1),
            transitionDirection = AppTransitionDirection.Pop,
        )
        else -> this
    }
    is AppNavigationEvent.OpenDetails -> reduce(
        AppNavigationEvent.Navigate(AppRoute.Details(event.animeId)),
    )
}
