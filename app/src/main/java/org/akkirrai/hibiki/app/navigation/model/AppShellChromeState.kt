package org.akkirrai.hibiki.app.navigation

/** Route-driven chrome policy used by the shared shell. */
fun appBottomBarVisible(
    selectedTab: AppDestination,
    currentRoute: AppRoute,
): Boolean = selectedTab != AppDestination.SETTINGS && currentRoute is AppRoute.TopLevel
