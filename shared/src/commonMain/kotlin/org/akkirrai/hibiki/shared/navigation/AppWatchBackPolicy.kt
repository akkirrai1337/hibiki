package org.akkirrai.hibiki.shared.navigation

/** Resume-from-Details has no watch-flow route to retain after Player is popped. */
fun shouldKeepWatchAnimeAfterPlayerBack(route: AppRoute): Boolean =
    route is AppRoute.WatchSources || route is AppRoute.Episodes
