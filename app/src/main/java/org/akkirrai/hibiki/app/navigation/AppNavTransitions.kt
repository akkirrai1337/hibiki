package org.akkirrai.hibiki.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import org.akkirrai.hibiki.core.design.AppMotion

fun appScreenEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionDurationMillis,
            easing = ScreenSlideEasing,
        ),
        initialOffsetX = { fullWidth -> fullWidth }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionFadeDurationMillis,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionDurationMillis,
            easing = ScreenSlideEasing,
        ),
        targetOffsetX = { fullWidth -> -fullWidth }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionFadeDurationMillis,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionDurationMillis,
            easing = ScreenSlideEasing,
        ),
        initialOffsetX = { fullWidth -> -fullWidth }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionFadeDurationMillis,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionDurationMillis,
            easing = ScreenSlideEasing,
        ),
        targetOffsetX = { fullWidth -> fullWidth }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = AppMotion.ScreenTransitionFadeDurationMillis,
            easing = FastOutSlowInEasing,
        )
    )
}

private val ScreenSlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
