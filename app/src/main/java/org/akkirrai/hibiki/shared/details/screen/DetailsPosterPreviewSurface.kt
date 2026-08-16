package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun AppDetailsPosterPreviewSurface(
    scrimAlpha: Float,
    posterAlpha: Float,
    posterScale: Float,
    onDismiss: () -> Unit,
    posterContent: @Composable (Modifier) -> Unit,
    backContent: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = onDismiss),
    ) {
        posterContent(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = posterAlpha
                    scaleX = posterScale
                    scaleY = posterScale
                },
        )
        Box(
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            backContent()
        }
    }
}
