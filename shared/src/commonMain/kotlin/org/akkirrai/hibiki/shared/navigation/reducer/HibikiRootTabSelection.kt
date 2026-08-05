package org.akkirrai.hibiki.shared.navigation

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
