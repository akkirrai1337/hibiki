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
}
