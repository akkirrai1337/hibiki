package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerBottomOverlay(
    positionLabel: String,
    durationMs: Long,
    bufferedPositionMs: Long,
    sliderPositionMs: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
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
            .navigationBarsPadding()
            .padding(
                start = PlayerBottomOverlayHorizontalPadding,
                end = PlayerBottomOverlayHorizontalPadding,
                top = PlayerBottomOverlayTopPadding,
                bottom = PlayerBottomOverlayBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            AppPlayerTimeline(
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                sliderPositionMs = sliderPositionMs,
                onSeekPreview = onSliderValueChange,
                onSeekFinished = onSliderValueChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 0.dp)
                    .offset(y = (-3).dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = positionLabel,
                    modifier = Modifier.padding(top = 1.dp),
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
