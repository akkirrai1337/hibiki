package org.akkirrai.hibiki.shared.navigation

fun AppNavigationState.reduceOverlayVisibilityChange(
    overlay: AppOverlay,
    visible: Boolean,
): AppNavigationState = if (visible) {
    reduce(AppNavigationEvent.PresentOverlay(overlay))
} else if (activeOverlay == overlay) {
    reduce(AppNavigationEvent.DismissOverlay)
} else {
    this
}
