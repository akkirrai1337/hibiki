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

fun appScreenEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        initialOffsetX = { fullWidth -> fullWidth }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        targetOffsetX = { fullWidth -> -fullWidth }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        initialOffsetX = { fullWidth -> -fullWidth }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        targetOffsetX = { fullWidth -> fullWidth }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun topLevelEnterTransition(
    initialRoute: String?,
    targetRoute: String?,
): EnterTransition {
    val direction = topLevelDirection(initialRoute, targetRoute)
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        initialOffsetX = { fullWidth -> direction * fullWidth },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun topLevelExitTransition(
    initialRoute: String?,
    targetRoute: String?,
): ExitTransition {
    val direction = topLevelDirection(initialRoute, targetRoute)
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_DURATION_MS,
            easing = ScreenSlideEasing,
        ),
        targetOffsetX = { fullWidth -> -direction * fullWidth },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = SCREEN_TRANSITION_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

private fun topLevelDirection(
    initialRoute: String?,
    targetRoute: String?,
): Int {
    val initialIndex = TopLevelDestination.entries.indexOfFirst { it.route == initialRoute }
    val targetIndex = TopLevelDestination.entries.indexOfFirst { it.route == targetRoute }
    if (initialIndex == -1 && targetIndex != -1) {
        return -1
    }
    if (targetIndex == -1 || initialIndex == targetIndex) {
        return 1
    }
    return if (targetIndex > initialIndex) 1 else -1
}

private val ScreenSlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val SCREEN_TRANSITION_DURATION_MS = 360
private const val SCREEN_TRANSITION_FADE_DURATION_MS = 260
