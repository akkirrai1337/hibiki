package org.akkirrai.hibiki.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import org.akkirrai.hibiki.core.design.AppMotion

fun appScreenEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    )
}

fun appScreenExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    )
}

fun appScreenPopEnterTransition(): EnterTransition {
    return appScreenEnterTransition()
}

fun appScreenPopExitTransition(): ExitTransition {
    return appScreenExitTransition()
}

/**
 * Main sections cross-fade. Unlike a fade-through transition, both surfaces remain visible for
 * the whole duration, so the app background never flashes between them.
 */
fun appTopLevelEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(durationMillis = 180),
    )
}

fun appTopLevelExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(durationMillis = 180),
    )
}
