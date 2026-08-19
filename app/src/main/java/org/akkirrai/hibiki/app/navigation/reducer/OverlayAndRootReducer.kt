package org.akkirrai.hibiki.app.navigation

fun AppNavigationState.reduceOverlayVisibilityChange(
    overlay: AppOverlay,
    visible: Boolean,
): AppNavigationState = if (visible) {
    reduce(AppNavigationEvent.PresentOverlay(overlay))
} else if (activeOverlay == overlay) {
    reduce(AppNavigationEvent.DismissOverlay)
} else {
    this
}

fun AppNavigationState.reduceDetailsOverlayChange(
    overlay: AppOverlay,
    open: Boolean,
): AppNavigationState = reduceOverlayVisibilityChange(overlay = overlay, visible = open)

/** Maps the shared destination model to the root navigation destination. */
fun AppDestination.toTopLevelDestination(): AppTopLevelDestination = when (this) {
    AppDestination.HOME -> AppTopLevelDestination.HOME
    AppDestination.CATALOG -> AppTopLevelDestination.CATALOG
    AppDestination.LIBRARY -> AppTopLevelDestination.LIBRARY
    AppDestination.SOURCES -> AppTopLevelDestination.SOURCES
    AppDestination.PROFILE,
    AppDestination.SETTINGS,
    -> AppTopLevelDestination.PROFILE
}

fun AppTopLevelDestination.toAppDestination(settingsVisible: Boolean = false): AppDestination = when (this) {
    AppTopLevelDestination.HOME -> AppDestination.HOME
    AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
    AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
    AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
    AppTopLevelDestination.PROFILE -> if (settingsVisible) AppDestination.SETTINGS else AppDestination.PROFILE
}

fun AppNavigationState.selectRootDestination(destination: AppDestination): AppNavigationState =
    reduce(AppNavigationEvent.SelectTopLevel(destination.toTopLevelDestination()))

data class HibikiRootTabSelectionResult(
    val handled: Boolean,
    val state: AppNavigationState,
)

fun reduceHibikiRootTabSelection(
    state: AppNavigationState,
    selectedTab: AppDestination,
    destination: AppDestination,
): HibikiRootTabSelectionResult {
    val target = destination.toTopLevelDestination()
    val current = state.currentTopLevel
    if (target == current && selectedTab == destination) {
        return HibikiRootTabSelectionResult(handled = false, state = state)
    }
    return HibikiRootTabSelectionResult(
        handled = true,
        state = state.selectRootDestination(destination),
    )
}
