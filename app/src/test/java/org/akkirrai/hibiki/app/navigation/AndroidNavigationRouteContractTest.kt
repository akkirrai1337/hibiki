package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

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
}
