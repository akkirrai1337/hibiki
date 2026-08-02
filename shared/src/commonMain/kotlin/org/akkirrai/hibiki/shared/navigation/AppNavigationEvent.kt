package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.model.Anime

enum class AppTopLevelDestination(val key: String) {
    HOME("home"),
    PROFILE("profile"),
    CATALOG("catalog"),
    LIBRARY("library"),
    SOURCES("sources"),
    SETTINGS("settings"),
}

/** Platform-neutral events consumed by Android Navigation, iOS navigation or a desktop host. */
sealed interface AppNavigationEvent {
    data object Back : AppNavigationEvent
    data class SelectTopLevel(val destination: AppTopLevelDestination) : AppNavigationEvent
    data class OpenDetails(val anime: Anime) : AppNavigationEvent
    data class OpenWatchSources(
        val animeId: String,
        val title: String = "",
        val downloadMode: Boolean = false,
    ) : AppNavigationEvent
    data class OpenEpisodes(
        val sourceId: String,
        val sourceTitle: String = "",
        val downloadMode: Boolean = false,
    ) : AppNavigationEvent
    data class OpenPlayer(
        val sourceId: String,
        val episodeId: String = "",
        val episodeNumber: Double? = null,
    ) : AppNavigationEvent
    data object OpenSettings : AppNavigationEvent
}
