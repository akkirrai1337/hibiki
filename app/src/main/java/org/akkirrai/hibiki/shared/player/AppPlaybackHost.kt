package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState

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
