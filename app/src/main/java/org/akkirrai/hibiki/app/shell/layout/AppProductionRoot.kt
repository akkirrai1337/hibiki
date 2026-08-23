package org.akkirrai.hibiki.app.shell.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.akkirrai.hibiki.design.AppMotion
import org.akkirrai.hibiki.design.component.navigation.AppBottomBar
import org.akkirrai.hibiki.app.navigation.AndroidNavigationRoute
import org.akkirrai.hibiki.app.navigation.AppNavigationEvent
import org.akkirrai.hibiki.app.navigation.AppTopLevelDestination
import org.akkirrai.hibiki.app.navigation.AppTransitionDirection
import org.akkirrai.hibiki.app.navigation.AppTransitionKey
import org.akkirrai.hibiki.app.navigation.AppRoute
import org.akkirrai.hibiki.app.navigation.isWatchFlowRoute
import org.akkirrai.hibiki.app.navigation.toAndroidNavigationRoute
import org.akkirrai.hibiki.text.appText

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
    tabContent: @Composable (AppTopLevelDestination) -> Unit,
    content: @Composable (AppTopLevelDestination, AppRoute?) -> Unit,
) {
    val targetRootState = AppRootContentState(
        destination = currentDestination,
        transitionKey = contentTransitionKey
            ?: AppTransitionKey("top-level", currentDestination.route),
        route = contentRoute,
    )
    // Details and the watch flow are full-screen destinations that replace the tab UI
    // entirely (see AppDestinationContent's own showBaseRoutes/early-return handling of the
    // same two cases). The tab layer fades out for the duration of that transition instead of
    // disappearing instantly, so it crossfades against the incoming Details/watch content the
    // same way the old single-AnimatedContent implementation did.
    //
    // Driven by the *same* Transition<AppRootContentState> as the content crossfade below
    // (updateTransition + transition.animateFloat/transition.AnimatedContent), not a second,
    // independently-triggered AnimatedVisibility -- two separately-triggered animations with
    // the same nominal duration are not guaranteed to start on the same frame, and any drift
    // between them read as a stray extra transition, worse the faster you toggle in and out.
    // Sharing one Transition makes that desync structurally impossible: both are just different
    // properties of the same clock.
    fun tabLayerAlphaFor(state: AppRootContentState): Float =
        if (state.route !is AppRoute.Details && state.route?.isWatchFlowRoute() != true) 1f else 0f
    val transition = updateTransition(targetState = targetRootState, label = "top_level_screen_transition")
    val tabLayerAlpha by transition.animateFloat(
        transitionSpec = { tween(AppMotion.ScreenTransitionDurationMillis) },
        label = "tab_layer_alpha",
    ) { state -> tabLayerAlphaFor(state) }
    Box(modifier = modifier.fillMaxSize()) {
        val navController = rememberNavController()
        val startTabRoute = remember { currentDestination.toTabRoute() }
        LaunchedEffect(currentDestination) {
            // inclusive = true: the tab backstack must never grow past one entry. Tabs have no
            // back-stack of their own today (switching tabs is always a full reset, never
            // undoable), and a NavHost with more than one backstack entry registers its own
            // system-back interception that fires ahead of AppSystemBackHandler -- with
            // inclusive = false that swallowed the gesture used to close Settings/Details
            // instead of letting it reach the app's own back coordinator.
            navController.navigate(currentDestination.toTabRoute()) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
        // NavHost's graph builder (the trailing lambda below) only runs once, memoized on
        // (navController, startDestination) -- so the composable() blocks close over whatever
        // `tabContent` was on the FIRST composition, not later ones. tabContent is a fresh
        // closure every recomposition of AppProductionRoot's caller (capturing that
        // recomposition's presenters/state), so without rememberUpdatedState the tab NavHost
        // would keep calling the very first tabContent forever.
        val currentTabContent = rememberUpdatedState(tabContent)
        // Always composed; visibility is purely tabLayerAlpha (see above), driven by the same
        // Transition the content crossfade below uses. Kept mounted regardless of alpha so its
        // NavController/backstack survive Details/watch-flow visits, same as before.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = tabLayerAlpha },
        ) {
            NavHost(
                navController = navController,
                startDestination = startTabRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
                exitTransition = { fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
                popEnterTransition = { fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
                popExitTransition = { fadeOut(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)) },
            ) {
                composable<AndroidNavigationRoute.Home> { currentTabContent.value(AppTopLevelDestination.HOME) }
                composable<AndroidNavigationRoute.Catalog> { currentTabContent.value(AppTopLevelDestination.CATALOG) }
                composable<AndroidNavigationRoute.Library> { currentTabContent.value(AppTopLevelDestination.LIBRARY) }
                composable<AndroidNavigationRoute.Sources> { currentTabContent.value(AppTopLevelDestination.SOURCES) }
                composable<AndroidNavigationRoute.Profile> { currentTabContent.value(AppTopLevelDestination.PROFILE) }
            }
        }
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { appScreenTransition(transitionDirection) },
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

private fun AppTopLevelDestination.toTabRoute(): AndroidNavigationRoute =
    AppRoute.TopLevel(this).toAndroidNavigationRoute()

internal fun appScreenTransition(direction: AppTransitionDirection) = when (direction) {
    AppTransitionDirection.Forward,
    AppTransitionDirection.Pop,
    -> fadeIn(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    ) togetherWith fadeOut(
        animationSpec = tween(AppMotion.ScreenTransitionDurationMillis),
    )
}
