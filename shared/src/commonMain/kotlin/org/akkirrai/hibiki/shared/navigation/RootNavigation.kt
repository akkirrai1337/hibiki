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

fun AppNavigationState.selectRootDestination(destination: AppDestination): AppNavigationState =
    reduce(AppNavigationEvent.SelectTopLevel(destination.toTopLevelDestination()))
