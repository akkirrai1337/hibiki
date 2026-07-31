package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlaybackControls

/** Android platform host for the common playback controls and Media3 transport. */
@Composable
internal fun AndroidCommonPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
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
        )
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
