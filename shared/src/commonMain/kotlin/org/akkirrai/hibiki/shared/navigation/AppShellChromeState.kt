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
