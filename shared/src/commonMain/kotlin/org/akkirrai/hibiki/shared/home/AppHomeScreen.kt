package org.akkirrai.hibiki.shared.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared Home composition boundary; search filters and host resources remain slots. */
@Composable
fun AppHomeScreen(
    searchActive: Boolean,
    searchContent: @Composable () -> Unit,
    personalContent: @Composable () -> Unit,
    topScrimContent: @Composable BoxScope.() -> Unit,
    searchBarContent: @Composable BoxScope.() -> Unit,
    filterContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = searchActive,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(tween(220), initialOffsetY = { it / 12 }) + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(200), targetOffsetY = { -(it / 24) }) + fadeOut(tween(120)))
                } else {
                    (slideInVertically(tween(220), initialOffsetY = { -(it / 24) }) + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(200), targetOffsetY = { it / 12 }) + fadeOut(tween(120)))
                }
            },
            label = "HomeSearchContent",
        ) { active ->
            if (active) searchContent() else personalContent()
        }
        Box(modifier = Modifier.fillMaxSize(), content = topScrimContent)
        Box(modifier = Modifier.fillMaxSize(), content = searchBarContent)
        filterContent()
    }
}
