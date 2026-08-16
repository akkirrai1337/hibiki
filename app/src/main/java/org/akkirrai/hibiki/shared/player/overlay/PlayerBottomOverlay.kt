package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppPlayerBottomOverlay(
    positionLabel: String,
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    timelineEnabled: Boolean = true,
    controlsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            )
            .padding(
                start = PlayerBottomOverlayHorizontalPadding,
                end = PlayerBottomOverlayHorizontalPadding,
                top = PlayerBottomOverlayTopPadding,
                bottom = PlayerBottomOverlayBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(PlayerBottomOverlayZeroSpacing),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PlayerBottomOverlayZeroSpacing),
        ) {
            AppPlayerTimeline(
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                sliderPositionMs = sliderPositionMs,
                onSeekPreview = onSliderValueChange,
                onSeekFinished = onSliderValueChangeFinished,
                enabled = timelineEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PlayerBottomOverlayTimelineTopPadding, bottom = PlayerBottomOverlayZeroSpacing)
                    .offset(y = PlayerBottomOverlayTimelineOffset),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = PlayerBottomOverlayControlsOffset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = positionLabel,
                    modifier = Modifier.padding(top = PlayerBottomOverlayPositionTopPadding),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                controlsContent()
            }
        }
    }
}
