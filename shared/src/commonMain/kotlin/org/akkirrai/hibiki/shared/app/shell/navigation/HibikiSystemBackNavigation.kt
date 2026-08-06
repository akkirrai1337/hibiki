package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationEvent
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.AppRoute
import org.akkirrai.hibiki.shared.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.shared.navigation.currentRoute
import org.akkirrai.hibiki.shared.navigation.reduce
import org.akkirrai.hibiki.shared.navigation.resolveWatchFlowBackEffect
import org.akkirrai.hibiki.shared.navigation.resolveWatchFlowBackTransition

internal enum class HibikiBackCleanup {
    None,
    ActivePlayback,
    Player,
    Episodes,
    Details,
}

internal data class HibikiSystemBackResult(
    val state: AppNavigationState,
    val handled: Boolean,
    val cleanup: HibikiBackCleanup = HibikiBackCleanup.None,
    val clearPlaybackReturnRoute: Boolean = false,
    val effect: WatchFlowBackEffect = WatchFlowBackEffect.None,
)

internal fun reduceHibikiSystemBack(
    navigationState: AppNavigationState,
    selectedTab: AppDestination,
    hasActivePlayback: Boolean,
    playbackReturnRoute: AppRoute?,
): HibikiSystemBackResult {
    val routeBeforeBack = navigationState.currentRoute
    if (navigationState.overlays.isNotEmpty()) {
        return HibikiSystemBackResult(navigationState.reduce(AppNavigationEvent.Back), handled = true)
    }
    if (selectedTab == AppDestination.SETTINGS) {
        val canPopSettings = routeBeforeBack is AppRoute.Settings
        return HibikiSystemBackResult(
            state = if (canPopSettings) navigationState.reduce(AppNavigationEvent.Back) else navigationState,
            handled = true,
        )
    }
    if (hasActivePlayback) {
        val transition = resolveWatchFlowBackTransition(navigationState, playbackReturnRoute)
        return HibikiSystemBackResult(
            state = transition.state,
            handled = true,
            cleanup = HibikiBackCleanup.ActivePlayback,
            clearPlaybackReturnRoute = true,
            effect = transition.effect,
        )
    }
    if (navigationState.backStack.isEmpty()) return HibikiSystemBackResult(navigationState, handled = false)
    val transition = resolveWatchFlowBackTransition(navigationState, playbackReturnRoute)
    val cleanup = when (routeBeforeBack) {
        is AppRoute.Player -> HibikiBackCleanup.Player
        is AppRoute.Episodes, is AppRoute.WatchSources -> HibikiBackCleanup.Episodes
        is AppRoute.Details -> if (
            resolveWatchFlowBackEffect(routeBeforeBack, transition.state.currentRoute) == WatchFlowBackEffect.CloseDetails
        ) HibikiBackCleanup.Details else HibikiBackCleanup.None
        else -> HibikiBackCleanup.None
    }
    return HibikiSystemBackResult(
        state = transition.state,
        handled = true,
        cleanup = cleanup,
        clearPlaybackReturnRoute = routeBeforeBack is AppRoute.Player,
        effect = transition.effect,
    )
}
