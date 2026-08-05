package org.akkirrai.hibiki.shared.app.destination.content

import org.akkirrai.hibiki.shared.app.destination.state.AppDestinationContentState
import org.akkirrai.hibiki.shared.navigation.AppRoute

internal fun AppDestinationContentState.isWatchRouteDriven(): Boolean =
    currentRoute?.let { route ->
        route is AppRoute.WatchSources || route is AppRoute.Episodes || route is AppRoute.Player
    } ?: (watchAnime != null)

internal fun AppDestinationContentState.isDetailsRouteDriven(): Boolean =
    currentRoute?.let { it is AppRoute.Details } ?: (selectedAnime != null)
