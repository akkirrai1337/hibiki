package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.feature.player.AndroidCommonPlaybackHost
import org.akkirrai.hibiki.feature.player.AndroidPlayerWindowController
import org.akkirrai.hibiki.feature.player.AndroidPlayerWindowMode
import org.akkirrai.hibiki.shared.player.AppPlaybackHost
import org.akkirrai.hibiki.shared.profile.PlaybackProgressRepository

internal fun androidSharedAppPlaybackHost(
    progressRepository: PlaybackProgressRepository,
    windowController: AndroidPlayerWindowController,
): AppPlaybackHost = { playback, playbackContext, navigationState, playbackLoading, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
    AndroidCommonPlaybackHost(
        playback = playback,
        context = playbackContext,
        navigationState = navigationState,
        playbackLoading = playbackLoading,
        progressRepository = progressRepository,
        windowController = windowController,
        onBack = onBack,
        onEpisodeSelected = onEpisodeSelected,
        onSettingsAction = onSettingsAction,
        onOverlayEvent = onOverlayEvent,
    )
}

@Composable
internal fun AndroidSharedAppPlayerWindowMode(
    active: Boolean,
    controller: AndroidPlayerWindowController,
    activity: Activity,
) {
    AndroidPlayerWindowMode(
        active = active,
        controller = controller,
        activity = activity,
    )
}
