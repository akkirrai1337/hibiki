package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppOverlay
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination

fun dispatchPlayerPlaylistOpen(onOverlayEvent: (AppNavigationEvent) -> Unit) {
    onOverlayEvent(AppNavigationEvent.PresentOverlay(AppOverlay.Playlist))
}

fun dispatchPlayerSettingsOpen(onOverlayEvent: (AppNavigationEvent) -> Unit) {
    onOverlayEvent(AppNavigationEvent.OpenPlayerSettings)
}

fun dispatchPlayerPlaylistDismiss(
    setControlsVisible: () -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    onOverlayEvent(AppNavigationEvent.DismissOverlay)
    setControlsVisible()
}

fun dispatchPlayerSettingsDismiss(
    setControlsVisible: () -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
    setControlsVisible()
}

fun dispatchPlayerLock(
    setLocked: () -> Unit,
    setControlsHidden: () -> Unit,
    playlistVisible: Boolean,
    settingsVisible: Boolean,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    setLocked()
    setControlsHidden()
    if (playlistVisible) onOverlayEvent(AppNavigationEvent.DismissOverlay)
    if (settingsVisible) onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
}

fun dispatchPlayerUnlock(
    setUnlocked: () -> Unit,
    setControlsVisible: () -> Unit,
) {
    setUnlocked()
    setControlsVisible()
}

fun dispatchPlayerSettingsDestination(
    destination: AppPlayerSettingsDestination,
    setControlsVisible: () -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    setControlsVisible()
    onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(destination))
}
