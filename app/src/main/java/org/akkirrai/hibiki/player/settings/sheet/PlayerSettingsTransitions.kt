package org.akkirrai.hibiki.player

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import org.akkirrai.hibiki.app.navigation.AppPlayerSettingsDestination as PlayerSettingsDestination

fun AnimatedContentTransitionScope<PlayerSettingsDestination>.playerSettingsPageTransition(): ContentTransform {
    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
    return (
        slideInHorizontally(animationSpec = tween(180)) { width -> direction * width / 5 } +
            fadeIn(animationSpec = tween(140))
        ).togetherWith(
            slideOutHorizontally(animationSpec = tween(180)) { width -> -direction * width / 5 } +
                fadeOut(animationSpec = tween(120))
        ).using(SizeTransform(clip = false))
}
