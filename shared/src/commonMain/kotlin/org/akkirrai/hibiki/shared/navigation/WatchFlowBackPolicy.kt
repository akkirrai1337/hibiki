package org.akkirrai.hibiki.shared.navigation

/** Side effects required after the common watch-flow reducer handles Back. */
enum class WatchFlowBackEffect {
    None,
    ResetPlayer,
    ResetEpisodesAndPlayer,
    CloseDetails,
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
