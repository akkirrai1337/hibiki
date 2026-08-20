package org.akkirrai.hibiki.app.navigation

import org.akkirrai.hibiki.design.AppMotion
import org.akkirrai.hibiki.player.model.WatchSource

/** The platform-neutral destinations used by the application shell. */
sealed interface AppRoute {
    data class TopLevel(val destination: AppTopLevelDestination) : AppRoute
    data class Details(val animeId: String) : AppRoute
    data object Settings : AppRoute
    data object SourceRepositories : AppRoute
    data class SourcePackageInfo(val repositoryUrl: String, val sourceId: String) : AppRoute
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
    data object DetailsPosterPreview : AppOverlay
    data object DetailsTitleSheet : AppOverlay
    data object DetailsLibrarySheet : AppOverlay
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

/** Sources -> episodes -> player for one anime, treated as a single continuous flow. */
fun AppRoute?.isWatchFlowRoute(): Boolean =
    this is AppRoute.WatchSources || this is AppRoute.Episodes || this is AppRoute.Player

fun AppRoute.transitionKey(): AppTransitionKey = when (this) {
    is AppRoute.TopLevel -> AppTransitionKey("top-level", destination.route)
    is AppRoute.Details -> AppTransitionKey("details", animeId)
    AppRoute.Settings -> AppTransitionKey("settings", "root")
    AppRoute.SourceRepositories -> AppTransitionKey("sources", "repositories")
    is AppRoute.SourcePackageInfo -> AppTransitionKey("sources", "package:$repositoryUrl:$sourceId")
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
