package org.akkirrai.hibiki.shared.navigation

data class AppNavigationState(
    val currentTopLevel: AppTopLevelDestination = AppTopLevelDestination.HOME,
    val backStack: List<AppRoute> = emptyList(),
    val overlays: List<AppOverlay> = emptyList(),
    val playerSettingsDestination: AppPlayerSettingsDestination = AppPlayerSettingsDestination.Root,
)

val AppNavigationState.currentRoute: AppRoute
    get() = backStack.lastOrNull() ?: AppRoute.TopLevel(currentTopLevel)

/** Applies Android's existing push/pop behavior without depending on a platform navigator. */
fun AppNavigationState.reduce(event: AppNavigationEvent): AppNavigationState = when (event) {
    is AppNavigationEvent.SelectTopLevel -> copy(
        currentTopLevel = event.destination,
        backStack = emptyList(),
        overlays = emptyList(),
        playerSettingsDestination = AppPlayerSettingsDestination.Root,
    )
    is AppNavigationEvent.Navigate -> when (val route = event.route) {
        is AppRoute.TopLevel -> reduce(AppNavigationEvent.SelectTopLevel(route.destination))
        else -> copy(backStack = backStack + route)
    }
    is AppNavigationEvent.Replace -> when (val route = event.route) {
        is AppRoute.TopLevel -> reduce(AppNavigationEvent.SelectTopLevel(route.destination))
        else -> copy(backStack = backStack.dropLast(1) + route)
    }
    is AppNavigationEvent.PresentOverlay -> copy(
        overlays = overlays + event.overlay,
        playerSettingsDestination = if (event.overlay is AppOverlay.PlayerSettings) {
            AppPlayerSettingsDestination.Root
        } else {
            playerSettingsDestination
        },
    )
    AppNavigationEvent.DismissOverlay -> copy(
        overlays = overlays.dropLast(1),
        playerSettingsDestination = if (overlays.lastOrNull() is AppOverlay.PlayerSettings) {
            AppPlayerSettingsDestination.Root
        } else {
            playerSettingsDestination
        },
    )
    is AppNavigationEvent.SetPlayerSettingsDestination -> copy(
        playerSettingsDestination = event.destination,
    )
    AppNavigationEvent.Back -> when {
        overlays.lastOrNull() is AppOverlay.PlayerSettings &&
            playerSettingsDestination != AppPlayerSettingsDestination.Root -> copy(
                playerSettingsDestination = AppPlayerSettingsDestination.Root,
            )
        overlays.isNotEmpty() -> reduce(AppNavigationEvent.DismissOverlay)
        backStack.isNotEmpty() -> copy(backStack = backStack.dropLast(1))
        else -> this
    }
    is AppNavigationEvent.OpenDetails -> reduce(
        AppNavigationEvent.Navigate(AppRoute.Details(event.animeId)),
    )
}
