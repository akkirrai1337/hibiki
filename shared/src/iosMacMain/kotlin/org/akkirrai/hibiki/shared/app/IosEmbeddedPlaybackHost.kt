package org.akkirrai.hibiki.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.AppPlayerFrame
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.player.IosComposePlayerControls
import org.akkirrai.hibiki.shared.player.IosPlayerSession
import org.akkirrai.hibiki.shared.player.IosPlayerSurface
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import platform.Foundation.NSDate

@Composable
internal fun IosEmbeddedPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) { IosPlayerSession(playback) }
    var playlistVisible by remember(session) { mutableStateOf(false) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)
    AppPlayerFrame {
        IosPlayerSurface(session, session.scaleMode, Modifier.fillMaxSize())
        IosComposePlayerControls(
            session = session,
            playback = playback,
            context = context,
            onBack = onBack,
            playlistEnabled = context.episodes.isNotEmpty(),
            onPlaylistClick = { playlistVisible = true },
            hasPreviousEpisode = episodeNavigation.hasPrevious,
            hasNextEpisode = episodeNavigation.hasNext,
            onPreviousEpisode = {
                resolveAdjacentEpisode(context.episodes, context.episodeId, context.episodeNumber, -1)
                    ?.let(onEpisodeSelected)
            },
            onNextEpisode = {
                resolveAdjacentEpisode(context.episodes, context.episodeId, context.episodeNumber, 1)
                    ?.let(onEpisodeSelected)
            },
        )
        AppPlayerPlaylistLayer(
            visible = playlistVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            headline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber).replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissRequest = { playlistVisible = false },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
            },
            nowMs = { (NSDate().timeIntervalSince1970 * 1_000.0).toLong() },
            backHandler = { _, _ -> },
        )
    }
}
