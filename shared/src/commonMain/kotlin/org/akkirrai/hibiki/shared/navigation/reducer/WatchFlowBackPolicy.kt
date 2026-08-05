package org.akkirrai.hibiki.shared.navigation

/** Side effects required after the common watch-flow reducer handles Back. */
enum class WatchFlowBackEffect {
    None,
    ResetPlayer,
    ResetEpisodesAndPlayer,
    CloseDetails,
}

data class WatchFlowBackTransition(
    val state: AppNavigationState,
    val effect: WatchFlowBackEffect,
)

fun AppNavigationState.reduceWatchFlowBack(returnRoute: AppRoute? = null): WatchFlowBackTransition {
    val routeBeforeBack = currentRoute
    val stateAfterBack = reduce(AppNavigationEvent.Back)
    val correctedState = if (
        routeBeforeBack is AppRoute.Player &&
        returnRoute is AppRoute.Episodes &&
        stateAfterBack.currentRoute != returnRoute
    ) {
        stateAfterBack.copy(backStack = stateAfterBack.backStack + returnRoute)
    } else {
        stateAfterBack
    }
    return WatchFlowBackTransition(
        state = correctedState,
        effect = resolveWatchFlowBackEffect(routeBeforeBack, correctedState.currentRoute),
    )
}

fun resolveWatchFlowBackEffect(
    routeBeforeBack: AppRoute,
    routeAfterBack: AppRoute,
): WatchFlowBackEffect = when {
    routeBeforeBack is AppRoute.Details -> WatchFlowBackEffect.CloseDetails
    routeBeforeBack is AppRoute.Episodes || routeBeforeBack is AppRoute.WatchSources ->
        WatchFlowBackEffect.ResetEpisodesAndPlayer
    routeBeforeBack is AppRoute.Player && routeAfterBack is AppRoute.Episodes ->
        WatchFlowBackEffect.ResetEpisodesAndPlayer
    routeBeforeBack is AppRoute.Player && routeAfterBack is AppRoute.WatchSources ->
        WatchFlowBackEffect.ResetEpisodesAndPlayer
    routeBeforeBack is AppRoute.Player -> WatchFlowBackEffect.ResetPlayer
    else -> WatchFlowBackEffect.None
}
