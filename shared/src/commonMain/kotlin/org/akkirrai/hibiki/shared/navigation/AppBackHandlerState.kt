package org.akkirrai.hibiki.shared.navigation

/** Whether the platform back bridge must be active for the current shared shell state. */
fun appBackHandlerEnabled(
    selectedTab: AppDestination,
    hasBackStack: Boolean,
    hasOverlay: Boolean,
    hasActivePlayback: Boolean,
): Boolean = selectedTab == AppDestination.SETTINGS ||
    hasBackStack ||
    hasOverlay ||
    hasActivePlayback
