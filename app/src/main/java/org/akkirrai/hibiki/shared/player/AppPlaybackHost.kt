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
    () -> Unit,
    (WatchEpisode) -> Unit,
    (PlaybackSettingsAction) -> Unit,
    (AppNavigationEvent) -> Unit,
) -> Unit
