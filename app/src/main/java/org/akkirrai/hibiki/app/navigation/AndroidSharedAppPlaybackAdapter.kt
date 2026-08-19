package org.akkirrai.hibiki.app.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.core.source.ResumeFrameRepository
import org.akkirrai.hibiki.player.AndroidCommonPlaybackHost
import org.akkirrai.hibiki.player.AndroidPlayerWindowController
import org.akkirrai.hibiki.player.AndroidPlayerWindowMode
import org.akkirrai.hibiki.player.AppPlaybackHost
import org.akkirrai.hibiki.profile.PlaybackProgressRepository

internal fun androidSharedAppPlaybackHost(
    progressRepository: PlaybackProgressRepository,
    resumeFrameRepository: ResumeFrameRepository,
    windowController: AndroidPlayerWindowController,
): AppPlaybackHost = { playback, playbackContext, navigationState, playbackLoading, onBack, onEpisodeSelected, onSettingsAction, onOverlayEvent ->
    AndroidCommonPlaybackHost(
        playback = playback,
        context = playbackContext,
        navigationState = navigationState,
        playbackLoading = playbackLoading,
        progressRepository = progressRepository,
        resumeFrameRepository = resumeFrameRepository,
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
