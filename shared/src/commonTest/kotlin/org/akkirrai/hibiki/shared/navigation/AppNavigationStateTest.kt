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

    @Test
    fun selectingProfileOrLibraryClearsNestedRouteAndOverlays() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Sheet("library")))

        val profile = state.reduce(AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.PROFILE))
        assertEquals(AppTopLevelDestination.PROFILE, profile.currentTopLevel)
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.PROFILE), profile.currentRoute)
        assertEquals(emptyList(), profile.backStack)
        assertEquals(emptyList(), profile.overlays)

        val library = profile.reduce(AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.LIBRARY))
        assertEquals(AppRoute.TopLevel(AppTopLevelDestination.LIBRARY), library.currentRoute)
        assertEquals(library, library.reduce(AppNavigationEvent.Back))
    }

    @Test
    fun playerSettingsDestinationIsSharedAndBackReturnsToRootBeforeDismiss() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Speed))

        val root = state.reduce(AppNavigationEvent.Back)
        assertEquals(AppPlayerSettingsDestination.Root, root.playerSettingsDestination)
        assertEquals(listOf(AppOverlay.PlayerSettings), root.overlays)

        val dismissed = root.reduce(AppNavigationEvent.Back)
        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(AppPlayerSettingsDestination.Root, dismissed.playerSettingsDestination)
    }
}
