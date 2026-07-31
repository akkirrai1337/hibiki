package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppShellTransitionKeyTest {
    @Test
    fun profileAndSettingsHaveDistinctStableIdentities() {
        val profile = appShellTransitionKey(
            topLevelDestination = AppTopLevelDestination.PROFILE,
            selectedTab = "PROFILE",
            detailsId = null,
            watchId = null,
            sourceId = null,
            routeKey = AppRoute.TopLevel(AppTopLevelDestination.PROFILE).transitionKey(),
        )
        val settings = appShellTransitionKey(
            topLevelDestination = AppTopLevelDestination.PROFILE,
            selectedTab = "SETTINGS",
            detailsId = null,
            watchId = null,
            sourceId = null,
            routeKey = AppRoute.TopLevel(AppTopLevelDestination.PROFILE).transitionKey(),
        )

        assertNotEquals(profile, settings)
        assertEquals(profile, profile.copy())
    }

    @Test
    fun nestedWatchRoutesKeepDistinctShellIdentities() {
        val details = appShellTransitionKey(
            topLevelDestination = AppTopLevelDestination.HOME,
            selectedTab = "HOME",
            detailsId = "anime-1",
            watchId = null,
            sourceId = null,
            routeKey = AppRoute.Details("anime-1").transitionKey(),
        )
        val sources = appShellTransitionKey(
            topLevelDestination = AppTopLevelDestination.HOME,
            selectedTab = "HOME",
            detailsId = "anime-1",
            watchId = "anime-1",
            sourceId = null,
            routeKey = AppRoute.WatchSources("anime-1").transitionKey(),
        )
        val episodes = appShellTransitionKey(
            topLevelDestination = AppTopLevelDestination.HOME,
            selectedTab = "HOME",
            detailsId = "anime-1",
            watchId = "anime-1",
            sourceId = "source-1",
            routeKey = AppRoute.Episodes(
                source = org.akkirrai.hibiki.shared.model.WatchSource("source-1", "Dub", 12),
                animeId = "anime-1",
            ).transitionKey(),
        )

        assertNotEquals(details, sources)
        assertNotEquals(sources, episodes)
        assertNotEquals(details, episodes)
    }
}
