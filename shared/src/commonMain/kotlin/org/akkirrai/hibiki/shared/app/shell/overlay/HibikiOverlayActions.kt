package org.akkirrai.hibiki.shared.app.shell.overlay

import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.reduceDetailsOverlayChange
import org.akkirrai.hibiki.shared.navigation.reduceOverlayVisibilityChange
import org.akkirrai.hibiki.shared.navigation.activeOverlay

internal class HibikiOverlayActions(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val libraryFilterOverlay: AppOverlay,
) {
    fun setLibraryFilterVisible(visible: Boolean) {
        if (!visible && navigationState().activeOverlay != libraryFilterOverlay) return
        setNavigationState(
            navigationState().reduceOverlayVisibilityChange(libraryFilterOverlay, visible),
        )
    }

    fun setDetailsPosterPreviewOpen(open: Boolean) {
        setNavigationState(
            navigationState().reduceDetailsOverlayChange(AppOverlay.DetailsPosterPreview, open),
        )
    }

    fun setDetailsTitleSheetOpen(open: Boolean) {
        setNavigationState(
            navigationState().reduceDetailsOverlayChange(AppOverlay.DetailsTitleSheet, open),
        )
    }

    fun setDetailsLibrarySheetOpen(open: Boolean) {
        setNavigationState(
            navigationState().reduceDetailsOverlayChange(AppOverlay.DetailsLibrarySheet, open),
        )
    }
}
