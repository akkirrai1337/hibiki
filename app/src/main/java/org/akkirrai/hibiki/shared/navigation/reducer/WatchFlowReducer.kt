package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.player.model.WatchSource

/** Resume-from-Details has no watch-flow route to retain after Player is popped. */
fun shouldKeepWatchAnimeAfterPlayerBack(route: AppRoute): Boolean =
    route is AppRoute.WatchSources || route is AppRoute.Episodes

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

internal fun resolveWatchFlowBackTransition(
    navigationState: AppNavigationState,
    playbackReturnRoute: AppRoute?,
): WatchFlowBackTransition = navigationState.reduceWatchFlowBack(playbackReturnRoute)

fun AppNavigationState.navigateToDetails(animeId: String): AppNavigationState = reduce(
    AppNavigationEvent.Navigate(AppRoute.Details(animeId)),
)

fun AppNavigationState.navigateBackFromDetails(): AppNavigationState = if (currentRoute is AppRoute.Details) {
    reduce(AppNavigationEvent.Back)
} else {
    this
}

fun AppNavigationState.navigateToSettings(): AppNavigationState = if (currentRoute is AppRoute.Settings) {
    this
} else {
    reduce(AppNavigationEvent.Navigate(AppRoute.Settings))
}

fun AppNavigationState.navigateToSourceRepositories(): AppNavigationState = if (
    currentRoute is AppRoute.SourceRepositories
) {
    this
} else {
    reduce(AppNavigationEvent.Navigate(AppRoute.SourceRepositories))
}

fun AppNavigationState.navigateToSourcePackageInfo(
    repositoryUrl: String,
    sourceId: String,
): AppNavigationState = reduce(
    AppNavigationEvent.Navigate(AppRoute.SourcePackageInfo(repositoryUrl, sourceId)),
)

fun AppNavigationState.navigateToWatchSources(
    animeId: String,
    downloadMode: Boolean = false,
): AppNavigationState = reduce(
    AppNavigationEvent.Navigate(
        AppRoute.WatchSources(
            animeId = animeId,
            downloadMode = downloadMode,
        ),
    ),
)

/** Builds the shared Sources -> Episodes transition used by every application host. */
fun AppNavigationState.navigateToEpisodes(
    source: WatchSource,
    downloadMode: Boolean = false,
    animeId: String? = null,
): AppNavigationState = reduce(
    AppNavigationEvent.Navigate(
        AppRoute.Episodes(
            source = source,
            downloadMode = downloadMode,
            animeId = animeId,
        ),
    ),
)

/** Uses Replace for an already visible player and Navigate for a new player route. */
fun AppNavigationState.navigateToPlayer(
    sourceId: String,
    episodeId: String,
    episodeNumber: Double? = null,
): AppNavigationState {
    val route = AppRoute.Player(
        sourceId = sourceId,
        episodeId = episodeId,
        episodeNumber = episodeNumber,
    )
    return reduce(
        if (currentRoute is AppRoute.Player) {
            AppNavigationEvent.Replace(route)
        } else {
            AppNavigationEvent.Navigate(route)
        },
    )
}
