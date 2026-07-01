package org.akkirrai.hibiki.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween

fun appScreenEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = NAV_ENTER_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth -> fullWidth / NAV_ENTER_OFFSET_DIVISOR }
    )
}

fun appScreenExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NAV_EXIT_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth -> -(fullWidth / NAV_EXIT_OFFSET_DIVISOR) }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NAV_EXIT_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun appScreenPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = NAV_ENTER_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth -> -(fullWidth / NAV_EXIT_OFFSET_DIVISOR) }
    )
}

fun appScreenPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NAV_EXIT_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth -> fullWidth / NAV_ENTER_OFFSET_DIVISOR }
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NAV_EXIT_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun topLevelEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_ENTER_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { fullWidth -> fullWidth / TOP_LEVEL_OFFSET_DIVISOR },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

fun topLevelExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_EXIT_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth -> -(fullWidth / TOP_LEVEL_OFFSET_DIVISOR) },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_FADE_DURATION_MS,
            easing = FastOutSlowInEasing,
        )
    )
}

private const val NAV_ENTER_DURATION_MS = 180
private const val NAV_EXIT_DURATION_MS = 180
private const val NAV_EXIT_FADE_DURATION_MS = 90
private const val TOP_LEVEL_ENTER_DURATION_MS = 180
private const val TOP_LEVEL_EXIT_DURATION_MS = 160
private const val TOP_LEVEL_FADE_DURATION_MS = 120
private const val NAV_ENTER_OFFSET_DIVISOR = 8
private const val NAV_EXIT_OFFSET_DIVISOR = 20
private const val TOP_LEVEL_OFFSET_DIVISOR = 10
