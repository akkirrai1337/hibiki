package org.akkirrai.hibiki.home.screen

import androidx.compose.animation.AnimatedContent
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
