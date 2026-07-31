package org.akkirrai.hibiki.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStateTest {
    @Test
    fun topLevelSelectionUpdatesCurrentDestination() {
        val state = AppNavigationState().reduce(
            AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.LIBRARY),
        )

        assertEquals(AppTopLevelDestination.LIBRARY, state.currentTopLevel)
    }

    @Test
    fun secondaryEventsDoNotChangeTopLevelDestination() {
        val state = AppNavigationState(AppTopLevelDestination.CATALOG)

        assertEquals(state, state.reduce(AppNavigationEvent.Back))
        assertEquals(
            AppRoute.Details("anime-1"),
            state.reduce(AppNavigationEvent.OpenDetails("anime-1")).currentRoute,
        )
    }
}
