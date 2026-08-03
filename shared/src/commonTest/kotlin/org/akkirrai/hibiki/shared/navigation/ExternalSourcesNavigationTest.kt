package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalSourcesNavigationTest {
    @Test
    fun `settings opens external sources as a nested route`() {
        val state = AppNavigationState()
            .navigateToSettings()
            .navigateToExternalSources()

        assertEquals(AppRoute.ExternalSources, state.currentRoute)
        assertEquals(AppDestination.SETTINGS, state.selectedAppDestination())
        assertEquals(
            listOf(AppRoute.Settings, AppRoute.ExternalSources),
            state.backStack,
        )
    }

    @Test
    fun `external sources back returns to settings`() {
        val state = AppNavigationState()
            .navigateToSettings()
            .navigateToExternalSources()
            .reduce(AppNavigationEvent.Back)

        assertEquals(AppRoute.Settings, state.currentRoute)
    }

    @Test
    fun `opening external sources repeatedly is idempotent`() {
        val state = AppNavigationState()
            .navigateToSettings()
            .navigateToExternalSources()

        assertEquals(state, state.navigateToExternalSources())
    }
}
