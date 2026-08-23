package org.akkirrai.hibiki.app.navigation

import kotlinx.serialization.Serializable
import org.akkirrai.hibiki.player.model.WatchSource

/**
 * Android Navigation's persistent destination contract.
 *
 * This is deliberately separate from [AppRoute] while the legacy reducer still owns
 * navigation. A future NavHost will use these routes as its only back stack; keeping the
 * payloads primitive makes Android state restoration independent of in-memory UI models.
 */
@Serializable
sealed interface AndroidNavigationRoute {
    companion object {
        /** The five destinations displayed by the bottom navigation bar. */
        val rootTabs: List<AndroidNavigationRoute> = listOf(
            Home,
            Catalog,
            Library,
            Sources,
            Profile,
        )
    }

    @Serializable
    data object Home : AndroidNavigationRoute

    @Serializable
    data object Catalog : AndroidNavigationRoute

    @Serializable
    data object Library : AndroidNavigationRoute

    @Serializable
    data object Sources : AndroidNavigationRoute

    @Serializable
    data object Profile : AndroidNavigationRoute

    @Serializable
    data class Details(val animeId: String) : AndroidNavigationRoute

    @Serializable
    data object Settings : AndroidNavigationRoute

    @Serializable
    data object SourceRepositories : AndroidNavigationRoute

    @Serializable
    data class SourcePackageInfo(
        val repositoryUrl: String,
        val sourceId: String,
    ) : AndroidNavigationRoute

    @Serializable
    data class WatchSources(
        val animeId: String,
        val downloadMode: Boolean = false,
    ) : AndroidNavigationRoute

    /**
     * All values are navigation arguments, rather than an in-memory source instance. Android may
     * restore this entry after process death, when the old in-memory source list no longer exists.
     */
    @Serializable
    data class Episodes(
        val animeId: String?,
        val sourceId: String,
        val sourceTitle: String,
        val episodeCount: Int?,
        val qualityLabel: String?,
        val sourceIsPriority: Boolean,
        val downloadMode: Boolean = false,
    ) : AndroidNavigationRoute

    @Serializable
    data class Player(
        val sourceId: String,
        val episodeId: String,
        val episodeNumber: Double? = null,
    ) : AndroidNavigationRoute
}

/**
 * Transitional conversion used only while the old reducer-backed shell is being replaced.
 *
 * Keeping this lossless makes it possible to move one NavHost boundary at a time without
 * changing a route's payload or the screen it represents.
 */
internal fun AppRoute.toAndroidNavigationRoute(): AndroidNavigationRoute = when (this) {
    is AppRoute.TopLevel -> when (destination) {
        AppTopLevelDestination.HOME -> AndroidNavigationRoute.Home
        AppTopLevelDestination.CATALOG -> AndroidNavigationRoute.Catalog
        AppTopLevelDestination.LIBRARY -> AndroidNavigationRoute.Library
        AppTopLevelDestination.SOURCES -> AndroidNavigationRoute.Sources
        AppTopLevelDestination.PROFILE -> AndroidNavigationRoute.Profile
    }
    is AppRoute.Details -> AndroidNavigationRoute.Details(animeId)
    AppRoute.Settings -> AndroidNavigationRoute.Settings
    AppRoute.SourceRepositories -> AndroidNavigationRoute.SourceRepositories
    is AppRoute.SourcePackageInfo -> AndroidNavigationRoute.SourcePackageInfo(repositoryUrl, sourceId)
    is AppRoute.WatchSources -> AndroidNavigationRoute.WatchSources(animeId, downloadMode)
    is AppRoute.Episodes -> AndroidNavigationRoute.Episodes(
        animeId = animeId,
        sourceId = source.sourceId,
        sourceTitle = source.title,
        episodeCount = source.episodeCount,
        qualityLabel = source.qualityLabel,
        sourceIsPriority = source.isPriority,
        downloadMode = downloadMode,
    )
    is AppRoute.Player -> AndroidNavigationRoute.Player(sourceId, episodeId, episodeNumber)
}

/** Converts a restored Android destination back to the current screen contract. */
internal fun AndroidNavigationRoute.toAppRoute(): AppRoute = when (this) {
    AndroidNavigationRoute.Home -> AppRoute.TopLevel(AppTopLevelDestination.HOME)
    AndroidNavigationRoute.Catalog -> AppRoute.TopLevel(AppTopLevelDestination.CATALOG)
    AndroidNavigationRoute.Library -> AppRoute.TopLevel(AppTopLevelDestination.LIBRARY)
    AndroidNavigationRoute.Sources -> AppRoute.TopLevel(AppTopLevelDestination.SOURCES)
    AndroidNavigationRoute.Profile -> AppRoute.TopLevel(AppTopLevelDestination.PROFILE)
    is AndroidNavigationRoute.Details -> AppRoute.Details(animeId)
    AndroidNavigationRoute.Settings -> AppRoute.Settings
    AndroidNavigationRoute.SourceRepositories -> AppRoute.SourceRepositories
    is AndroidNavigationRoute.SourcePackageInfo -> AppRoute.SourcePackageInfo(repositoryUrl, sourceId)
    is AndroidNavigationRoute.WatchSources -> AppRoute.WatchSources(animeId, downloadMode)
    is AndroidNavigationRoute.Episodes -> AppRoute.Episodes(
        source = WatchSource(
            sourceId = sourceId,
            title = sourceTitle,
            episodeCount = episodeCount,
            qualityLabel = qualityLabel,
            isPriority = sourceIsPriority,
        ),
        downloadMode = downloadMode,
        animeId = animeId,
    )
    is AndroidNavigationRoute.Player -> AppRoute.Player(sourceId, episodeId, episodeNumber)
}
