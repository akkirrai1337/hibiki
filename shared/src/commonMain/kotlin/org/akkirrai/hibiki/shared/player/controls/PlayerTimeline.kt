package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

@Composable
fun AppPlayerTimeline(
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val playedFraction = (sliderPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val playedColor = Color(0xFFE53935)
    val bufferedColor = playedColor.copy(alpha = 0.34f)
    val trackColor = Color.White.copy(alpha = 0.18f)
    val thumbRadiusPx = with(LocalDensity.current) { (PlayerTimelineThumbSize / 2).toPx() }

    fun seekFromX(x: Float) {
        val fraction = (x / trackWidthPx).coerceIn(0f, 1f)
        onSeekPreview((safeDuration * fraction).toLong())
    }

    val interactionModifier = if (enabled) {
        Modifier
            .pointerInput(safeDuration, trackWidthPx) {
                detectTapGestures(
                    onTap = { offset ->
                        seekFromX(offset.x)
                        onSeekFinished()
                    },
                )
            }
            .pointerInput(safeDuration, trackWidthPx) {
                detectDragGestures(
                    onDragStart = { offset -> seekFromX(offset.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        seekFromX(change.position.x)
                    },
                    onDragEnd = onSeekFinished,
                    onDragCancel = onSeekFinished,
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(PlayerTimelineContainerHeight)
            .then(interactionModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerTimelineTrackHeight)
                .clip(RoundedCornerShape(PlayerTimelineTrackCornerRadius))
                .background(trackColor)
                .onSizeChanged { trackWidthPx = it.width.toFloat() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(PlayerTimelineTrackCornerRadius))
                    .background(bufferedColor),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(PlayerTimelineTrackCornerRadius))
                    .background(playedColor),
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (trackWidthPx * playedFraction) - thumbRadiusPx
                }
                .size(PlayerTimelineThumbSize)
                .clip(CircleShape)
                .background(playedColor),
        )
    }
}
