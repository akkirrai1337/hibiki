package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.AppTransitionKey
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.currentTransitionKey
import org.akkirrai.hibiki.shared.navigation.selectedAppDestination
import org.akkirrai.hibiki.shared.navigation.toTopLevelDestination

internal data class HibikiShellRoutePresentation(
    val selectedTab: AppDestination,
    val topLevelDestination: org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination,
    val currentRoute: AppRoute,
    val currentTransitionKey: AppTransitionKey,
    val activeDownloadMode: Boolean,
) {
    val isPlayerRoute: Boolean
        get() = currentRoute is AppRoute.Player
}

internal fun AppNavigationState.toHibikiShellRoutePresentation(): HibikiShellRoutePresentation {
    val selectedTab = selectedAppDestination()
    return HibikiShellRoutePresentation(
        selectedTab = selectedTab,
        topLevelDestination = selectedTab.toTopLevelDestination(),
        currentRoute = currentRoute,
        currentTransitionKey = currentTransitionKey,
        activeDownloadMode = currentRoute.downloadModeEnabled(),
    )
}
