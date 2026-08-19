package org.akkirrai.hibiki.details.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

private const val POSTER_PREVIEW_DISMISS_DELAY_MS = 180L

@Composable
fun AppDetailsPosterPreviewOverlay(
    onDismissRequest: () -> Unit,
    backHandler: @Composable (onBack: () -> Unit) -> Unit,
    posterContent: @Composable (posterModifier: Modifier) -> Unit,
    backContent: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun dismissAnimated() {
        if (isDismissing) return
        isDismissing = true
        isVisible = false
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(POSTER_PREVIEW_DISMISS_DELAY_MS)
            onDismissRequest()
        }
    }

    backHandler(::dismissAnimated)

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.78f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "posterPreviewScrimAlpha",
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterAlpha",
    )
    val posterScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 220),
        label = "posterPreviewPosterScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = ::dismissAnimated),
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
            backContent(::dismissAnimated)
        }
    }
}
