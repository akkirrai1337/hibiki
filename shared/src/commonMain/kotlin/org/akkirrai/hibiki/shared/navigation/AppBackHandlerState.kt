package org.akkirrai.hibiki.shared.navigation

/** Whether the platform back bridge must be active for the current shared shell route. */
fun appBackHandlerEnabled(
    selectedTab: AppDestination,
    currentRoute: AppRoute,
    hasOverlay: Boolean,
): Boolean = selectedTab == AppDestination.SETTINGS ||
    currentRoute !is AppRoute.TopLevel ||
    hasOverlay

fun appBackHandlerEnabled(state: AppNavigationState): Boolean =
    appBackHandlerEnabled(
        selectedTab = state.selectedAppDestination(),
        currentRoute = state.currentRoute,
        hasOverlay = state.activeOverlay != null,
    )
