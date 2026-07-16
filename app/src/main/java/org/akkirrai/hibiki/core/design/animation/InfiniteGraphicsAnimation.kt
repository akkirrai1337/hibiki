package org.akkirrai.hibiki.core.design.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Runs a continuous float animation directly in a graphics layer.
 *
 * Reading the animated value inside [graphicsLayer] keeps each frame out of composition and layout.
 * The transform can animate layer-backed properties such as rotation, alpha, scale, and translation.
 */
@Composable
fun Modifier.infiniteGraphicsAnimation(
    durationMillis: Int,
    initialValue: Float = 0f,
    targetValue: Float = 1f,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Restart,
    enabled: Boolean = true,
    label: String = "infinite_graphics_animation",
    transform: GraphicsLayerScope.(Float) -> Unit,
): Modifier {
    if (!enabled) return this

    val transition = rememberInfiniteTransition(label = label)
    val animatedValue = transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis.coerceAtLeast(1),
                easing = easing,
            ),
            repeatMode = repeatMode,
        ),
        label = label,
    )
    return graphicsLayer {
        transform(animatedValue.value)
    }
}

/** Continuously rotates any composable without recomposing it on every frame. */
@Composable
fun Modifier.continuousRotation(
    durationMillis: Int = 10_000,
    initialAngle: Float = 0f,
    targetAngle: Float = 360f,
    enabled: Boolean = true,
    label: String = "continuous_rotation",
): Modifier = infiniteGraphicsAnimation(
    durationMillis = durationMillis,
    initialValue = initialAngle,
    targetValue = targetAngle,
    enabled = enabled,
    label = label,
) { angle ->
    rotationZ = angle
}
