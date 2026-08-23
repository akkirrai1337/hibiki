package org.akkirrai.hibiki.app.navigation

import kotlinx.serialization.Serializable

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
