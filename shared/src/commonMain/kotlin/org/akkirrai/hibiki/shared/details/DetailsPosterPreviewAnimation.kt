package org.akkirrai.hibiki.shared.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun AppDetailsPosterPreviewAnimation(
    visible: Boolean,
    content: @Composable (
        scrimAlpha: Float,
        posterAlpha: Float,
        posterScale: Float,
    ) -> Unit,
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.78f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "posterPreviewScrimAlpha",
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterAlpha",
    )
    val posterScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterScale",
    )

    content(scrimAlpha, posterAlpha, posterScale)
}
