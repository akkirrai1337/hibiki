package org.akkirrai.hibiki.core.design.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Pulsing placeholder block used to build skeleton loading states (poster art, text lines, pills,
 * etc.) - shared so every screen's "not loaded yet" content breathes with the same rhythm.
 */
@Composable
fun AppShimmerBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "appShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appShimmerAlpha",
    )
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.14f)),
    )
}
