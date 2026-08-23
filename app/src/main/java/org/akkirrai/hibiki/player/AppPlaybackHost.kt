package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackSelection
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.model.WatchSource
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppNavigationState

/** Platform-owned playback surface hosted by the shared player shell. */
typealias AppPlaybackHost = @Composable (
    PlaybackStream?,
    PlaybackContext,
    AppNavigationState,
    Boolean, // playbackLoading -- e.g. a new episode's stream is being fetched
    () -> Unit,
    // (selected episode, the context it was chosen from) -- the request coordinator has no other
    // reliable way to know the current title's source/episodes when the player was opened
    // straight from Home/Catalog rather than via the Episodes tab.
    (WatchEpisode, PlaybackContext) -> Unit,
    (PlaybackSettingsAction) -> Unit,
    (AppNavigationEvent) -> Unit,
) -> Unit

/** Platform-owned playback integration points used by the shared app shell. */
class AppPlaybackPlatformCallbacks(
    val onPlaybackReady: (PlaybackStream, PlaybackContext) -> Unit = { _, _ -> },
    val onPlaybackSelectionChanged: (PlaybackSelection) -> Unit = {},
    val loadPlaybackSelection: (String) -> PlaybackSelection? = { null },
    val playbackHost: AppPlaybackHost? = null,
    val playerWindowMode: @Composable (Boolean) -> Unit = {},
    val onWatchSourceSelected: (String, WatchSource) -> Unit = { _, _ -> },
    // Asks the platform player to save its progress/resume frame before the system back gesture
    // tears down its session -- the in-player back button already does this itself.
    val onExitPlayback: () -> Unit = {},
)
