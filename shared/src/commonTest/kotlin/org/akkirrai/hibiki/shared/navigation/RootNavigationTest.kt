package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class RootNavigationTest {
    @Test
    fun destinationsMapToTheirSharedRootRoutes() {
        assertEquals(AppTopLevelDestination.HOME, AppDestination.HOME.toTopLevelDestination())
        assertEquals(AppTopLevelDestination.CATALOG, AppDestination.CATALOG.toTopLevelDestination())
        assertEquals(AppTopLevelDestination.LIBRARY, AppDestination.LIBRARY.toTopLevelDestination())
        assertEquals(AppTopLevelDestination.SOURCES, AppDestination.SOURCES.toTopLevelDestination())
        assertEquals(AppTopLevelDestination.PROFILE, AppDestination.PROFILE.toTopLevelDestination())
        assertEquals(AppTopLevelDestination.PROFILE, AppDestination.SETTINGS.toTopLevelDestination())
        assertEquals(AppDestination.HOME, AppTopLevelDestination.HOME.toAppDestination())
        assertEquals(AppDestination.CATALOG, AppTopLevelDestination.CATALOG.toAppDestination())
        assertEquals(AppDestination.LIBRARY, AppTopLevelDestination.LIBRARY.toAppDestination())
        assertEquals(AppDestination.SOURCES, AppTopLevelDestination.SOURCES.toAppDestination())
        assertEquals(AppDestination.PROFILE, AppTopLevelDestination.PROFILE.toAppDestination())
        assertEquals(AppDestination.SETTINGS, AppTopLevelDestination.PROFILE.toAppDestination(settingsVisible = true))
    }

    @Test
    fun selectingSettingsUsesProfileRootAndClearsNestedState() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.DetailsTitleSheet))
            .selectRootDestination(AppDestination.SETTINGS)

        assertEquals(AppTopLevelDestination.PROFILE, state.currentTopLevel)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.PROFILE), state.currentRoute)
        assertEquals(emptyList(), state.overlays)
    }
}
