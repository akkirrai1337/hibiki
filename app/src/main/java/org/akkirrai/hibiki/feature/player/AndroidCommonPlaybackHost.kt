package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlayerPlaylistLayer
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.formatEpisodeNumber
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.model.WatchEpisode

/** Android platform host for the common playback controls and Media3 transport. */
@Composable
internal fun AndroidCommonPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
    onEpisodeSelected: (WatchEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val androidContext = LocalContext.current
    val preferences = LocalAppPreferences.current
    val preferencesState = LocalAppPreferencesState.current
    val exoPlayer = remember(androidContext, playback.streamUrl, playback.headers) {
        ExoPlayer.Builder(androidContext).build()
    }
    val transport = remember(exoPlayer) { AndroidMedia3PlaybackTransport(exoPlayer) }
    var videoAspectRatio by remember { mutableFloatStateOf(DEFAULT_VIDEO_ASPECT_RATIO) }
    var playlistVisible by remember { mutableStateOf(false) }
    val videoScaleMode = preferencesState.videoScaleMode

    DisposableEffect(exoPlayer, playback.streamUrl) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    videoAspectRatio = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio /
                        videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.setMediaSource(playback.toAndroidMediaSource(androidContext))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidPlayerSurface(
            exoPlayer = exoPlayer,
            isAudioOnly = false,
            videoScaleMode = videoScaleMode,
            videoAspectRatio = videoAspectRatio,
            isClosing = false,
            onAttached = {},
        )
        AppPlaybackControls(
            transport = transport,
            playback = playback,
            context = context,
            scaleMode = videoScaleMode,
            onScaleClick = { preferences.setVideoScaleMode(videoScaleMode.next()) },
            onBack = onBack,
            playlistEnabled = context.episodes.isNotEmpty(),
            onPlaylistClick = { playlistVisible = true },
        )
        AppPlayerPlaylistLayer(
            visible = playlistVisible,
            currentEpisodeId = context.episodeId,
            episodes = context.episodes,
            headline = { episode ->
                appText(AppTextKey.PlayerEpisodeNumber)
                    .replace("%s", formatEpisodeNumber(episode.number))
            },
            onDismissRequest = { playlistVisible = false },
            onEpisodeClick = { episodeId ->
                context.episodes.firstOrNull { it.id == episodeId }?.let(onEpisodeSelected)
            },
            nowMs = SystemClock::elapsedRealtime,
            backHandler = { enabled, callback -> BackHandler(enabled = enabled, onBack = callback) },
        )
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
