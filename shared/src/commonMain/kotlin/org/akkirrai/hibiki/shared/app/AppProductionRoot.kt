package org.akkirrai.hibiki.shared.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.AppMotion
import org.akkirrai.hibiki.shared.design.component.AppTopLevelScaffold
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.AppTransitionDirection
import org.akkirrai.hibiki.shared.navigation.AppTransitionKey
import org.akkirrai.hibiki.shared.text.appText

/** Shared production shell used by platform hosts while they own screen orchestration. */
@Composable
fun AppProductionRoot(
    currentDestination: AppTopLevelDestination,
    onNavigationEvent: (AppNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<AppTopLevelDestination> = AppTopLevelDestination.entries,
    showBottomBar: Boolean = true,
    includeNavigationBarPadding: Boolean = true,
    contentTransitionKey: AppTransitionKey? = null,
    transitionDirection: AppTransitionDirection = AppTransitionDirection.Forward,
    iconContent: @Composable (AppTopLevelDestination, Modifier) -> Unit = { destination, iconModifier ->
        androidx.compose.material3.Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    content: @Composable (AppTopLevelDestination) -> Unit,
) {
    AppTopLevelScaffold(
        currentDestination = currentDestination,
        onDestinationClick = { destination ->
            onNavigationEvent(AppNavigationEvent.SelectTopLevel(destination))
        },
        iconContent = iconContent,
        label = { destination -> appText(destination.labelKey) },
        destinations = destinations,
        showBottomBar = showBottomBar,
        includeNavigationBarPadding = includeNavigationBarPadding,
        modifier = modifier,
        content = {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = AppRootContentState(
                    destination = currentDestination,
                    transitionKey = contentTransitionKey
                        ?: AppTransitionKey("top-level", currentDestination.route),
                ),
                transitionSpec = { appRootTransition(transitionDirection) },
                label = "top_level_screen_transition",
            ) { state ->
                content(state.destination)
            }
        },
    )
}

private data class AppRootContentState(
    val destination: AppTopLevelDestination,
    val transitionKey: AppTransitionKey,
)

private fun appRootTransition(direction: AppTransitionDirection): ContentTransform {
    return when (direction) {
        AppTransitionDirection.Forward,
        AppTransitionDirection.Pop,
        -> fadeIn(
            animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
        ) togetherWith fadeOut(
            animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
        )
    }
}
