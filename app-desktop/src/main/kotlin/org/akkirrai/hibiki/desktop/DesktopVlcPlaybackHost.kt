package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.shared.model.PlaybackContext
import org.akkirrai.hibiki.shared.model.PlaybackStream
import org.akkirrai.hibiki.shared.player.AppPlaybackControls
import org.akkirrai.hibiki.shared.player.VideoScaleMode

/** Embedded Desktop video surface with the shared playback controls layered above it. */
@Composable
internal fun DesktopVlcPlaybackHost(
    playback: PlaybackStream,
    context: PlaybackContext,
    onBack: () -> Unit,
) {
    val session = remember(playback.streamUrl, playback.headers) {
        DesktopVlcPlaybackSession(playback)
    }
    var scaleMode by remember(session) { mutableStateOf(VideoScaleMode.FIT) }
    var videoWidth by remember(session) { mutableIntStateOf(0) }
    var videoHeight by remember(session) { mutableIntStateOf(0) }
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
        val sourceAspect = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            0f
        }
        val viewportAspect = if (viewportWidth > 0f && viewportHeight > 0f) {
            viewportWidth / viewportHeight
        } else {
            0f
        }
        val cropScale = if (sourceAspect > 0f && viewportAspect > 0f) {
            maxOf(sourceAspect / viewportAspect, viewportAspect / sourceAspect)
        } else {
            1f
        }
        val stretchX = if (sourceAspect > 0f && viewportAspect > 0f) {
            if (sourceAspect >= viewportAspect) 1f else viewportAspect / sourceAspect
        } else {
            1f
        }
        val stretchY = if (sourceAspect > 0f && viewportAspect > 0f) {
            if (sourceAspect >= viewportAspect) sourceAspect / viewportAspect else 1f
        } else {
            1f
        }
        Box(modifier = Modifier.fillMaxSize()) {
            SwingPanel(
                factory = { session.component },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (scaleMode) {
                            VideoScaleMode.FIT -> Unit
                            VideoScaleMode.CROP -> {
                                scaleX = cropScale
                                scaleY = cropScale
                            }
                            VideoScaleMode.STRETCH -> {
                                scaleX = stretchX
                                scaleY = stretchY
                            }
                        }
                    },
                update = {},
            )
            AppPlaybackControls(
                transport = session.transport,
                playback = playback,
                context = context,
                scaleMode = scaleMode,
                onScaleClick = { scaleMode = scaleMode.next() },
                onBack = onBack,
            )
        }
    }
}

private const val VideoDimensionPollMillis = 500L
