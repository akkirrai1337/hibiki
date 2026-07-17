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
