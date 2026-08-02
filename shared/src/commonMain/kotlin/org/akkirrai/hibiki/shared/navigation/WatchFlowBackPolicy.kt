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

fun AppNavigationState.reduceWatchFlowBack(): WatchFlowBackTransition {
    val routeBeforeBack = currentRoute
    val stateAfterBack = reduce(AppNavigationEvent.Back)
    return WatchFlowBackTransition(
        state = stateAfterBack,
        effect = resolveWatchFlowBackEffect(routeBeforeBack, stateAfterBack.currentRoute),
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
