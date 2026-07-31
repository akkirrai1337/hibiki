package org.akkirrai.hibiki.shared.navigation

import org.akkirrai.hibiki.shared.design.AppMotion
import org.akkirrai.hibiki.shared.model.WatchSource

/** The platform-neutral destinations used by the application shell. */
sealed interface AppRoute {
    data class TopLevel(val destination: AppTopLevelDestination) : AppRoute
    data class Details(val animeId: String) : AppRoute
    data object Settings : AppRoute
    data class WatchSources(val animeId: String, val downloadMode: Boolean = false) : AppRoute
    data class Episodes(
        val source: WatchSource,
        val downloadMode: Boolean = false,
        val animeId: String? = null,
    ) : AppRoute
    data class Player(
        val sourceId: String,
        val episodeId: String,
        val episodeNumber: Double? = null,
    ) : AppRoute
}

/** Modal UI belongs to the same common stack and is dismissed before routes. */
sealed interface AppOverlay {
    data object Playlist : AppOverlay
    data object PlayerSettings : AppOverlay
    data class Dialog(val id: String) : AppOverlay
    data class Sheet(val id: String) : AppOverlay
}

enum class AppPlayerSettingsDestination {
    Root,
    Speed,
    Voiceover,
    Player,
    Quality,
}

data class AppTransitionKey(val route: String, val identity: String)

fun AppRoute.transitionKey(): AppTransitionKey = when (this) {
    is AppRoute.TopLevel -> AppTransitionKey("top-level", destination.route)
    is AppRoute.Details -> AppTransitionKey("details", animeId)
    AppRoute.Settings -> AppTransitionKey("settings", "root")
    is AppRoute.WatchSources -> AppTransitionKey(
        "watch-sources",
        "$animeId:$downloadMode",
    )
    is AppRoute.Episodes -> AppTransitionKey(
        "episodes",
        "${animeId.orEmpty()}:${source.sourceId}:$downloadMode",
    )
    is AppRoute.Player -> AppTransitionKey("player", "$sourceId:$episodeId")
}

enum class AppTransitionDirection { Forward, Pop }

data class AppTransitionSpec(
    val enterKey: AppTransitionKey,
    val exitKey: AppTransitionKey?,
    val durationMillis: Int = AppMotion.ScreenTransitionDurationMillis,
    val direction: AppTransitionDirection = AppTransitionDirection.Forward,
) {
    companion object {
        const val DefaultDurationMillis = AppMotion.ScreenTransitionDurationMillis
    }
}

fun appTransitionSpec(
    from: AppRoute?,
    to: AppRoute,
    direction: AppTransitionDirection,
): AppTransitionSpec = AppTransitionSpec(
    enterKey = to.transitionKey(),
    exitKey = from?.transitionKey(),
    direction = direction,
)
