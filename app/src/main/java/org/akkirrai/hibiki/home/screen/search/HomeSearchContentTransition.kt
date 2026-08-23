package org.akkirrai.hibiki.home.screen

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppHomeContentSwitcher(
    isSearchActive: Boolean,
    modifier: Modifier = Modifier,
    searchContent: @Composable () -> Unit,
    feedContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = isSearchActive,
        modifier = modifier,
        transitionSpec = { appHomeSearchContentTransition(targetState) },
        label = "HomeSearchContent",
    ) { searchActive ->
        if (searchActive) searchContent() else feedContent()
    }
}

fun appHomeSearchContentTransition(searchActive: Boolean): ContentTransform {
    return if (searchActive) {
        slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> fullHeight / 12 },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
            slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> -(fullHeight / 24) },
            ) + fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> -(fullHeight / 24) },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
            slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> fullHeight / 12 },
            ) + fadeOut(animationSpec = tween(durationMillis = 120))
    }
}
