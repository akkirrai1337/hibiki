package org.akkirrai.hibiki.shared.navigation


internal fun resolveWatchFlowBackTransition(
    navigationState: AppNavigationState,
    playbackReturnRoute: AppRoute?,
): WatchFlowBackTransition {
    val routeBeforeBack = navigationState.currentRoute
    val transition = navigationState.reduceWatchFlowBack()
    val correctedState = if (
        routeBeforeBack is AppRoute.Player &&
        playbackReturnRoute is AppRoute.Episodes &&
        transition.state.currentRoute != playbackReturnRoute
    ) {
        transition.state.copy(backStack = transition.state.backStack + playbackReturnRoute)
    } else {
        transition.state
    }
    return WatchFlowBackTransition(
        state = correctedState,
        effect = resolveWatchFlowBackEffect(routeBeforeBack, correctedState.currentRoute),
    )
}
