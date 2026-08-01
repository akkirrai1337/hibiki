package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppOverlay

fun dispatchPlayerPlaylistOpen(onOverlayEvent: (AppNavigationEvent) -> Unit) {
    onOverlayEvent(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
}

fun dispatchPlayerSettingsOpen(onOverlayEvent: (AppNavigationEvent) -> Unit) {
    onOverlayEvent(AppNavigationEvent.OpenPlayerSettings)
}

fun dispatchPlayerOverlayDismissalsForLock(
    playlistVisible: Boolean,
    settingsVisible: Boolean,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    if (playlistVisible) onOverlayEvent(AppNavigationEvent.DismissOverlay)
    if (settingsVisible) onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
}
