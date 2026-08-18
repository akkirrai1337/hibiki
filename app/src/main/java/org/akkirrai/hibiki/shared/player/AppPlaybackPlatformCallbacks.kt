package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchSource

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
