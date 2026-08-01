package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.model.WatchSource

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
