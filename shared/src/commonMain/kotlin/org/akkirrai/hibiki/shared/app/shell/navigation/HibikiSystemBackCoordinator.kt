package org.akkirrai.hibiki.shared.app.shell.navigation

import org.akkirrai.hibiki.shared.navigation.AppDestination
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.navigation.WatchFlowBackEffect
import org.akkirrai.hibiki.shared.player.HibikiPlaybackSession

internal class HibikiSystemBackCoordinator(
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val selectedTab: () -> AppDestination,
    private val playbackSession: HibikiPlaybackSession,
    private val hasActivePlayback: () -> Boolean,
    private val playbackReturnRoute: () -> org.akkirrai.hibiki.shared.navigation.AppRoute?,
    private val setPlaybackReturnRoute: (org.akkirrai.hibiki.shared.navigation.AppRoute?) -> Unit,
    private val applyWatchFlowBackEffect: (WatchFlowBackEffect) -> Unit,
    private val closeDetails: () -> Unit,
) {
    fun handle() {
        val result = reduceHibikiSystemBack(
            navigationState = navigationState(),
            selectedTab = selectedTab(),
            hasActivePlayback = hasActivePlayback(),
            playbackReturnRoute = playbackReturnRoute(),
        )
        if (!result.handled) return
        if (result.cleanup == HibikiBackCleanup.Details) closeDetails()
        setNavigationState(result.state)
        if (result.clearPlaybackReturnRoute) setPlaybackReturnRoute(null)
        when (result.cleanup) {
            HibikiBackCleanup.ActivePlayback -> {
                playbackSession.cancelAndInvalidate()
                playbackSession.clearRouteState()
                applyWatchFlowBackEffect(result.effect)
            }
            HibikiBackCleanup.Player -> {
                playbackSession.cancelJob()
                playbackSession.clearRouteState()
                applyWatchFlowBackEffect(result.effect)
            }
            HibikiBackCleanup.Episodes -> {
                playbackSession.cancelAndInvalidate()
                playbackSession.clearRouteState()
                applyWatchFlowBackEffect(result.effect)
            }
            HibikiBackCleanup.Details -> Unit
            HibikiBackCleanup.None -> Unit
        }
    }
}

internal fun createHibikiSystemBackCoordinator(
    navigationState: () -> AppNavigationState,
    setNavigationState: (AppNavigationState) -> Unit,
    selectedTab: () -> AppDestination,
    playbackSession: HibikiPlaybackSession,
    hasActivePlayback: () -> Boolean,
    playbackReturnRoute: () -> org.akkirrai.hibiki.shared.navigation.AppRoute?,
    setPlaybackReturnRoute: (org.akkirrai.hibiki.shared.navigation.AppRoute?) -> Unit,
    applyWatchFlowBackEffect: (WatchFlowBackEffect) -> Unit,
    closeDetails: () -> Unit,
): HibikiSystemBackCoordinator = HibikiSystemBackCoordinator(
    navigationState = navigationState,
    setNavigationState = setNavigationState,
    selectedTab = selectedTab,
    playbackSession = playbackSession,
    hasActivePlayback = hasActivePlayback,
    playbackReturnRoute = playbackReturnRoute,
    setPlaybackReturnRoute = setPlaybackReturnRoute,
    applyWatchFlowBackEffect = applyWatchFlowBackEffect,
    closeDetails = closeDetails,
)
