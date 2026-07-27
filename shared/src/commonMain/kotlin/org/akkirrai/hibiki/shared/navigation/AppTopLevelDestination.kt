package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.text.AppTextKey

/** Stable top-level destinations shared by every platform host. */
enum class AppTopLevelDestination(
    val route: String,
    val labelKey: AppTextKey,
) {
    HOME("home", AppTextKey.Home),
    CATALOG("catalog", AppTextKey.Catalog),
    LIBRARY("library", AppTextKey.Library),
    SOURCES("sources", AppTextKey.Sources),
    PROFILE("profile", AppTextKey.Profile),
}

sealed interface AppNavigationEvent {
    data class SelectTopLevel(val destination: AppTopLevelDestination) : AppNavigationEvent
    data object Back : AppNavigationEvent
    data class OpenDetails(val animeId: String) : AppNavigationEvent
}
