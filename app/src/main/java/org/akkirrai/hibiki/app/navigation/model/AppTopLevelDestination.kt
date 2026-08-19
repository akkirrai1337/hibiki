package org.akkirrai.hibiki.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.text.AppTextKey

/** Stable top-level destinations shared by every platform host. */
enum class AppTopLevelDestination(
    val route: String,
    val labelKey: AppTextKey,
    val icon: ImageVector,
) {
    HOME("home", AppTextKey.Home, Icons.Outlined.Home),
    CATALOG("catalog", AppTextKey.Catalog, Icons.Outlined.Explore),
    LIBRARY("library", AppTextKey.Library, Icons.Outlined.VideoLibrary),
    SOURCES("sources", AppTextKey.Sources, Icons.Outlined.Dns),
    PROFILE("profile", AppTextKey.Profile, Icons.Outlined.Person),
}

sealed interface AppNavigationEvent {
    data class SelectTopLevel(val destination: AppTopLevelDestination) : AppNavigationEvent
    data class Navigate(val route: AppRoute) : AppNavigationEvent
    data class Replace(val route: AppRoute) : AppNavigationEvent
    data class PresentOverlay(val overlay: AppOverlay) : AppNavigationEvent
    data class SetPlayerSettingsDestination(val destination: AppPlayerSettingsDestination) : AppNavigationEvent
    data object OpenPlayerSettings : AppNavigationEvent
    data object ClosePlayerSettings : AppNavigationEvent
    data object DismissOverlay : AppNavigationEvent
    data object Back : AppNavigationEvent
    data class OpenDetails(val animeId: String) : AppNavigationEvent {
        constructor(anime: Anime) : this(anime.id)
    }
    data object OpenSettings : AppNavigationEvent
}
