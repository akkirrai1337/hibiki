package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppOverlay
import org.akkirrai.hibiki.shared.navigation.AppPlayerSettingsDestination

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
    dispatchPlayerOverlayDismissalsForLock(
        playlistVisible = playlistVisible,
        settingsVisible = settingsVisible,
        onOverlayEvent = onOverlayEvent,
    )
}

fun dispatchPlayerUnlock(
    setUnlocked: () -> Unit,
    setControlsVisible: () -> Unit,
) {
    setUnlocked()
    setControlsVisible()
}

fun dispatchPlayerOverlayDismissalsForLock(
    playlistVisible: Boolean,
    settingsVisible: Boolean,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    if (playlistVisible) onOverlayEvent(AppNavigationEvent.DismissOverlay)
    if (settingsVisible) onOverlayEvent(AppNavigationEvent.ClosePlayerSettings)
}

fun dispatchPlayerSettingsDestination(
    destination: AppPlayerSettingsDestination,
    setControlsVisible: () -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    setControlsVisible()
    onOverlayEvent(AppNavigationEvent.SetPlayerSettingsDestination(destination))
}

fun dispatchPlayerSettingsRoot(
    setControlsVisible: () -> Unit,
    onOverlayEvent: (AppNavigationEvent) -> Unit,
) {
    dispatchPlayerSettingsDestination(
        destination = AppPlayerSettingsDestination.Root,
        setControlsVisible = setControlsVisible,
        onOverlayEvent = onOverlayEvent,
    )
}
