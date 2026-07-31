package org.akkirrai.hibiki.shared.navigation

/** Shared root chrome visibility matching Android's nested-screen behavior. */
fun appBottomBarVisible(
    selectedTab: AppDestination,
    hasDetails: Boolean,
    hasWatchFlow: Boolean,
    hasActivePlayback: Boolean,
): Boolean = selectedTab != AppDestination.SETTINGS &&
    !hasDetails &&
    !hasWatchFlow &&
    !hasActivePlayback

/** Route-driven chrome policy used by the shared shell. */
fun appBottomBarVisible(
    selectedTab: AppDestination,
    currentRoute: AppRoute,
): Boolean = selectedTab != AppDestination.SETTINGS && currentRoute is AppRoute.TopLevel
