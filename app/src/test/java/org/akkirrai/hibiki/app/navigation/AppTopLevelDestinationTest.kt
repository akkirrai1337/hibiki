package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTopLevelDestinationTest {
    @Test
    fun preservesAndroidTopLevelOrderAndRoutes() {
        assertEquals(
            listOf(
                AppTopLevelDestination.HOME,
                AppTopLevelDestination.CATALOG,
                AppTopLevelDestination.LIBRARY,
                AppTopLevelDestination.SOURCES,
                AppTopLevelDestination.PROFILE,
            ),
            AppTopLevelDestination.entries,
        )
        assertEquals(
            listOf("home", "catalog", "library", "sources", "profile"),
            AppTopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun navigationEventsCarrySharedIntent() {
        assertEquals(
            AppTopLevelDestination.CATALOG,
            AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.CATALOG).destination,
        )
        assertEquals("anime-42", AppNavigationEvent.OpenDetails("anime-42").animeId)
    }
}
