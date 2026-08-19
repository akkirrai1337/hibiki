package org.akkirrai.hibiki.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HibikiRootTabSelectionTest {
    @Test
    fun selectingCurrentRootIsIgnored() {
        val state = AppNavigationState()
        val result = reduceHibikiRootTabSelection(state, AppDestination.HOME, AppDestination.HOME)

        assertFalse(result.handled)
        assertEquals(state, result.state)
    }

    @Test
    fun selectingSettingsUsesProfileRootAndClearsNestedState() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
        val result = reduceHibikiRootTabSelection(state, AppDestination.HOME, AppDestination.SETTINGS)

        assertTrue(result.handled)
        assertEquals(AppTopLevelDestination.PROFILE, result.state.currentTopLevel)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.PROFILE), result.state.currentRoute)
    }
}
