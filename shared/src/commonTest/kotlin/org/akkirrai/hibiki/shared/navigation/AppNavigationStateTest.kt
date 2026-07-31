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
    fun selectingTopLevelResetsPlayerSettingsDestination() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality))

        val root = state.reduce(AppNavigationEvent.SelectTopLevel(AppTopLevelDestination.HOME))

        assertEquals(emptyList(), root.backStack)
        assertEquals(emptyList(), root.overlays)
        assertEquals(AppPlayerSettingsDestination.Root, root.playerSettingsDestination)
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

    @Test
    fun backDismissesPlaylistBeforePlayerRoute() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source", "episode", 1.0)))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))

        val dismissed = state.reduce(AppNavigationEvent.Back)
        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(AppRoute.Player("source", "episode", 1.0), dismissed.currentRoute)
    }

    @Test
    fun dismissingPlayerSettingsResetsNestedDestination() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality))

        val dismissed = state.reduce(AppNavigationEvent.DismissOverlay)
        assertEquals(emptyList(), dismissed.overlays)
        assertEquals(AppPlayerSettingsDestination.Root, dismissed.playerSettingsDestination)
    }

    @Test
    fun playerSettingsOpenAndCloseAreAtomicOverlayEvents() {
        val opened = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality))
            .reduce(AppNavigationEvent.OpenPlayerSettings)

        assertEquals(listOf(AppOverlay.PlayerSettings), opened.overlays)
        assertEquals(AppPlayerSettingsDestination.Root, opened.playerSettingsDestination)

        val closed = opened
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Speed))
            .reduce(AppNavigationEvent.ClosePlayerSettings)

        assertEquals(emptyList(), closed.overlays)
        assertEquals(AppPlayerSettingsDestination.Root, closed.playerSettingsDestination)
    }

    @Test
    fun detailsPosterPreviewParticipatesInOverlayBackStack() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.DetailsPosterPreview))

        assertEquals(listOf(AppOverlay.DetailsPosterPreview), state.overlays)
        assertEquals(AppRoute.Details("anime-1"), state.reduce(AppNavigationEvent.Back).currentRoute)
        assertEquals(emptyList(), state.reduce(AppNavigationEvent.Back).overlays)
    }

    @Test
    fun detailsTitleSheetParticipatesInOverlayBackStack() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.DetailsTitleSheet))

        assertEquals(listOf(AppOverlay.DetailsTitleSheet), state.overlays)
        assertEquals(emptyList(), state.reduce(AppNavigationEvent.Back).overlays)
        assertEquals(AppRoute.Details("anime-1"), state.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun detailsLibrarySheetParticipatesInOverlayBackStack() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.DetailsLibrarySheet))

        assertEquals(listOf(AppOverlay.DetailsLibrarySheet), state.overlays)
        assertEquals(emptyList(), state.reduce(AppNavigationEvent.Back).overlays)
        assertEquals(AppRoute.Details("anime-1"), state.reduce(AppNavigationEvent.Back).currentRoute)
    }

    @Test
    fun playbackOverlaysAreMutuallyExclusive() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))

        assertEquals(listOf(AppOverlay.PlayerSettings), state.overlays)
        assertEquals(
            emptyList(),
            state.reduce(AppNavigationEvent.Back).overlays,
        )
    }

    @Test
    fun replacingNestedPlayerSettingsWithPlaylistResetsSettingsDestination() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))

        assertEquals(AppPlayerSettingsDestination.Root, state.playerSettingsDestination)
        assertEquals(listOf(AppOverlay.Playlist), state.overlays)
    }

    @Test
    fun backOrderIsNestedDestinationThenOverlayThenPlayerRoute() {
        val state = AppNavigationState()
            .reduce(AppNavigationEvent.Navigate(AppRoute.Details("anime-1")))
            .reduce(AppNavigationEvent.Navigate(AppRoute.Player("source-1", "episode-1")))
            .reduce(AppNavigationEvent.PresentOverlay(AppOverlay.PlayerSettings))
            .reduce(AppNavigationEvent.SetPlayerSettingsDestination(AppPlayerSettingsDestination.Quality))

        val root = state.reduce(AppNavigationEvent.Back)
        assertEquals(AppPlayerSettingsDestination.Root, root.playerSettingsDestination)
        assertEquals(listOf(AppOverlay.PlayerSettings), root.overlays)

        val player = root.reduce(AppNavigationEvent.Back)
        assertEquals(emptyList(), player.overlays)
        assertEquals(AppRoute.Player("source-1", "episode-1"), player.currentRoute)

        assertEquals(AppRoute.Details("anime-1"), player.reduce(AppNavigationEvent.Back).currentRoute)
    }
}
