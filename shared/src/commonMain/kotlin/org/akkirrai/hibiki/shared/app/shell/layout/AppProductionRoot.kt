package org.akkirrai.hibiki.shared.app.shell.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.akkirrai.hibiki.shared.design.AppMotion
import org.akkirrai.hibiki.shared.design.component.navigation.AppBottomBar
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.shared.navigation.AppTransitionDirection
import org.akkirrai.hibiki.shared.navigation.AppTransitionKey
import org.akkirrai.hibiki.shared.navigation.AppRoute
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
    contentRoute: AppRoute? = null,
    transitionDirection: AppTransitionDirection = AppTransitionDirection.Forward,
    iconContent: @Composable (AppTopLevelDestination, Modifier) -> Unit = { destination, iconModifier ->
        androidx.compose.material3.Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    content: @Composable (AppTopLevelDestination, AppRoute?) -> Unit,
) {
    val targetRootState = AppRootContentState(
        destination = currentDestination,
        transitionKey = contentTransitionKey
            ?: AppTransitionKey("top-level", currentDestination.route),
        route = contentRoute,
    )
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = targetRootState,
            transitionSpec = { appScreenTransition(transitionDirection) },
            label = "top_level_screen_transition",
        ) { state ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (state == targetRootState) 1f else 0f),
            ) {
                content(state.destination, state.route)
            }
        }
        AnimatedVisibility(
            visible = showBottomBar,
            enter = fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)),
            exit = fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AppBottomBar(
                destinations = destinations,
                currentDestination = currentDestination,
                onDestinationClick = { destination ->
                    onNavigationEvent(AppNavigationEvent.SelectTopLevel(destination))
                },
                iconContent = iconContent,
                label = { destination -> appText(destination.labelKey) },
                includeNavigationBarPadding = includeNavigationBarPadding,
            )
        }
    }
}

private data class AppRootContentState(
    val destination: AppTopLevelDestination,
    val transitionKey: AppTransitionKey,
    val route: AppRoute?,
)

internal fun appScreenTransition(direction: AppTransitionDirection) = when (direction) {
    AppTransitionDirection.Forward,
    AppTransitionDirection.Pop,
    -> fadeIn(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    ) togetherWith fadeOut(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    )
}
