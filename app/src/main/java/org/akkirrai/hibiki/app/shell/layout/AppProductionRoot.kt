package org.akkirrai.hibiki.app.shell.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
    // same two cases). The tab NavHost below fades out for the duration of that transition
    // instead of disappearing instantly, so it crossfades against the incoming Details/watch
    // content the same way the old single-AnimatedContent implementation did.
    val tabLayerVisible = contentRoute !is AppRoute.Details && contentRoute?.isWatchFlowRoute() != true
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
        // Asymmetric on purpose. Hiding the tab layer (entering Details/watch-flow) is instant,
        // not faded: a symmetric AnimatedVisibility fade here ran as a second animation system
        // alongside the AnimatedContent crossfade below and the two could visibly desync (a
        // stray extra transition on open, or a frame of the wrong content while toggling
        // quickly) -- Details' own content fades in on top regardless, so hiding the tab
        // underneath immediately reads the same as fading it.
        //
        // But revealing it (leaving Details/watch-flow back to a tab) does still need its own
        // fade: Details' inner AnimatedContent (see AppDestinationContent) uses
        // EnterTransition.None/ExitTransition.None deliberately, since it used to rely on the
        // outer AnimatedContent's crossfade for its own visible fade -- but the outer
        // AnimatedContent no longer renders any tab content on the "leaving" side (that moved to
        // this NavHost in an earlier change), so there's nothing left for it to fade. Without
        // this reveal fade, Details would cut to the tab instantly with no transition at all.
        // This doesn't reintroduce the desync risk above: only one side (enter) animates here,
        // so there's no second clock running concurrently with the outer crossfade's own fade.
        AnimatedVisibility(
            visible = tabLayerVisible,
            enter = fadeIn(animationSpec = tween(AppMotion.ScreenTransitionDurationMillis)),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize(),
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
