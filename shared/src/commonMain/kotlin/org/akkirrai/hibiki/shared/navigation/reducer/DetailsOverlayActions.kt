package org.akkirrai.hibiki.shared.navigation

fun AppNavigationState.reduceDetailsOverlayChange(
    overlay: AppOverlay,
    open: Boolean,
): AppNavigationState = reduceOverlayVisibilityChange(overlay = overlay, visible = open)
