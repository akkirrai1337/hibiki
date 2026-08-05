package org.akkirrai.hibiki.shared.navigation

/** Maps the shared destination model to the root navigation destination. */
fun AppDestination.toTopLevelDestination(): AppTopLevelDestination = when (this) {
    AppDestination.HOME -> AppTopLevelDestination.HOME
    AppDestination.CATALOG -> AppTopLevelDestination.CATALOG
    AppDestination.LIBRARY -> AppTopLevelDestination.LIBRARY
    AppDestination.SOURCES -> AppTopLevelDestination.SOURCES
    AppDestination.PROFILE,
    AppDestination.SETTINGS,
    -> AppTopLevelDestination.PROFILE
}

fun AppTopLevelDestination.toAppDestination(settingsVisible: Boolean = false): AppDestination = when (this) {
    AppTopLevelDestination.HOME -> AppDestination.HOME
    AppTopLevelDestination.CATALOG -> AppDestination.CATALOG
    AppTopLevelDestination.LIBRARY -> AppDestination.LIBRARY
    AppTopLevelDestination.SOURCES -> AppDestination.SOURCES
    AppTopLevelDestination.PROFILE -> if (settingsVisible) AppDestination.SETTINGS else AppDestination.PROFILE
}

fun AppNavigationState.selectRootDestination(destination: AppDestination): AppNavigationState =
    reduce(AppNavigationEvent.SelectTopLevel(destination.toTopLevelDestination()))
