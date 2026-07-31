package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlayerUnlockOverlay
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.PlaybackSettingsAction
import org.akkirrai.hibiki.shared.player.PlayerUnlockBottomPadding
import org.akkirrai.hibiki.shared.player.VideoScaleMode
import org.akkirrai.hibiki.shared.player.resolveAdjacentEpisode
import org.akkirrai.hibiki.shared.player.resolveEpisodeNavigationAvailability
import org.akkirrai.hibiki.shared.player.resolvePlaybackViewportScale
import org.akkirrai.hibiki.shared.platform.AppSystemBackHandler
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

/** Embedded Desktop video surface with the shared playback controls layered above it. */
@Composable
internal fun DesktopVlcPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    onSettingsAction: (PlaybackSettingsAction) -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) {
        DesktopVlcPlaybackSession(playback)
    }
    var scaleMode by remember(session) { mutableStateOf(VideoScaleMode.FIT) }
    var videoWidth by remember(session) { mutableIntStateOf(0) }
    var videoHeight by remember(session) { mutableIntStateOf(0) }
    var playlistVisible by remember(session) { mutableStateOf(false) }
    var controlsLocked by remember(session) { mutableStateOf(false) }
    var unlockButtonVisible by remember(session) { mutableStateOf(false) }
    val episodeNavigation = resolveEpisodeNavigationAvailability(context.episodes, context.episodeId)
    DisposableEffect(session) {
        onDispose { session.release() }
    }
    LaunchedEffect(session) {
        while (true) {
            session.videoDimensions()?.let { (width, height) ->
                videoWidth = width
                videoHeight = height
            }
            delay(VideoDimensionPollMillis)
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val scale = resolvePlaybackViewportScale(
            mode = scaleMode,
            sourceWidth = videoWidth,
            sourceHeight = videoHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        AppSystemBackHandler(
            enabled = playlistVisible,
            onBack = { playlistVisible = false },
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SwingPanel(
                factory = { session.component },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (scaleMode) {
                            VideoScaleMode.FIT -> Unit
                            VideoScaleMode.CROP -> {
                                scaleX = scale.scaleX
                                scaleY = scale.scaleY
                            }
                            VideoScaleMode.STRETCH -> {
                                scaleX = scale.scaleX
                                scaleY = scale.scaleY
                            }
                        }
                    },
                update = {},
            )
            if (!controlsLocked) {
                AppPlaybackControls(
                    transport = session.transport,
                    playback = playback,
                    context = context,
                    scaleMode = scaleMode,
                    onScaleClick = { scaleMode = scaleMode.next() },
                    onBack = onBack,
                    playlistEnabled = context.episodes.isNotEmpty(),
                    onPlaylistClick = { playlistVisible = true },
                    hasPreviousEpisode = episodeNavigation.hasPrevious,
                    hasNextEpisode = episodeNavigation.hasNext,
                    onPreviousEpisode = {
                        resolveAdjacentEpisode(
                            context.episodes,
                            context.episodeId,
                            context.episodeNumber,
                            -1,
                        )?.let(onEpisodeSelected)
                    },
                    onNextEpisode = {
                        resolveAdjacentEpisode(
                            context.episodes,
                            context.episodeId,
                            context.episodeNumber,
                            1,
                        )?.let(onEpisodeSelected)
                    },
                    onLockClick = {
                        controlsLocked = true
                        unlockButtonVisible = true
                        playlistVisible = false
                    },
                    lockContentDescription = appText(AppTextKey.PlayerLock),
                )
            }
            AppPlayerUnlockOverlay(
                visible = controlsLocked && unlockButtonVisible,
                label = appText(AppTextKey.PlayerUnlock),
                onClick = {
                    controlsLocked = false
                    unlockButtonVisible = false
                },
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = PlayerUnlockBottomPadding),
            )
            AppPlayerPlaylistLayer(
                visible = playlistVisible,
                currentEpisodeId = context.episodeId,
                episodes = context.episodes,
                headline = { episode ->
                    appText(AppTextKey.PlayerEpisodeNumber)
                        .replace("%s", org.akkirrai.hibiki.shared.player.formatEpisodeNumber(episode.number))
                },
                onDismissRequest = { playlistVisible = false },
                onEpisodeClick = { episodeId ->
                    context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
                },
                nowMs = { System.currentTimeMillis() },
                backHandler = { enabled, callback ->
                    AppSystemBackHandler(enabled = enabled, onBack = callback) {}
                },
            )
        }
        }
    }
}

private const val VideoDimensionPollMillis = 500L
