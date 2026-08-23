package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.akkirrai.hibiki.player.model.WatchSource

class AndroidNavigationRouteContractTest {
    private val json = Json

    @Test
    fun `all root tabs are independent persistent destinations`() {
        assertEquals(
            listOf(
                AndroidNavigationRoute.Home,
                AndroidNavigationRoute.Catalog,
                AndroidNavigationRoute.Library,
                AndroidNavigationRoute.Sources,
                AndroidNavigationRoute.Profile,
            ),
            AndroidNavigationRoute.rootTabs,
        )
    }

    @Test
    fun `settings remains a profile child rather than a root tab`() {
        assertEquals(
            AndroidNavigationRoute.Settings,
            json.decodeFromString<AndroidNavigationRoute>(
                json.encodeToString<AndroidNavigationRoute>(AndroidNavigationRoute.Settings),
            ),
        )
    }

    @Test
    fun `watch route payload survives Android state restoration`() {
        val route: AndroidNavigationRoute = AndroidNavigationRoute.Episodes(
            animeId = "anime-1",
            sourceId = "source-1",
            sourceTitle = "Dub",
            episodeCount = 12,
            qualityLabel = "1080p",
            sourceIsPriority = true,
            downloadMode = true,
        )

        assertEquals(route, json.decodeFromString<AndroidNavigationRoute>(json.encodeToString(route)))
    }

    @Test
    fun `legacy routes preserve their payload through the Android contract`() {
        val routes = listOf(
            AppRoute.TopLevel(AppTopLevelDestination.HOME),
            AppRoute.TopLevel(AppTopLevelDestination.CATALOG),
            AppRoute.TopLevel(AppTopLevelDestination.LIBRARY),
            AppRoute.TopLevel(AppTopLevelDestination.SOURCES),
            AppRoute.TopLevel(AppTopLevelDestination.PROFILE),
            AppRoute.Details("anime-1"),
            AppRoute.Settings,
            AppRoute.SourceRepositories,
            AppRoute.SourcePackageInfo("https://example.com/repository.json", "source-1"),
            AppRoute.WatchSources("anime-1", downloadMode = true),
            AppRoute.Episodes(
                source = WatchSource("source-1", "Dub", 12, "1080p", isPriority = true),
                downloadMode = true,
                animeId = "anime-1",
            ),
            AppRoute.Player("source-1", "episode-1", 1.0),
        )

        routes.forEach { route ->
            assertEquals(route, route.toAndroidNavigationRoute().toAppRoute())
        }
    }
}
