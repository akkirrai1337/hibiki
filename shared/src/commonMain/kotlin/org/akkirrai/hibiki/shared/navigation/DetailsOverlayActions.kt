package org.akkirrai.hibiki.shared.navigation

fun AppNavigationState.reduceDetailsOverlayChange(
    overlay: AppOverlay,
    open: Boolean,
): AppNavigationState = if (open) {
    reduce(AppNavigationEvent.PresentOverlay(overlay))
} else if (activeOverlay == overlay) {
    reduce(AppNavigationEvent.DismissOverlay)
} else {
    this
}
