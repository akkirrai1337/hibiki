package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.model.WatchSource

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

fun AppNavigationState.navigateToExternalSources(): AppNavigationState = if (
    currentRoute is AppRoute.ExternalSources
) {
    this
} else {
    reduce(AppNavigationEvent.Navigate(AppRoute.ExternalSources))
}

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
