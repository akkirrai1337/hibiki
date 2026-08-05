package org.akkirrai.hibiki.shared.app.shell.player

import org.akkirrai.hibiki.shared.navigation.resolveWatchFlowBackTransition
import org.akkirrai.hibiki.shared.navigation.AppNavigationState
import org.akkirrai.hibiki.shared.player.PlaybackRequest
import org.akkirrai.hibiki.shared.player.HibikiPlaybackSession

internal class HibikiAppShellPlaybackOverlayActions(
    private val playbackSession: HibikiPlaybackSession,
    private val playbackEffects: HibikiAppShellPlaybackEffects,
    private val playbackRequestCoordinator: HibikiPlaybackRequestCoordinator,
    private val navigationState: () -> AppNavigationState,
    private val setNavigationState: (AppNavigationState) -> Unit,
    private val playbackReturnRoute: () -> org.akkirrai.hibiki.shared.navigation.AppRoute?,
    private val setPlaybackReturnRoute: (org.akkirrai.hibiki.shared.navigation.AppRoute?) -> Unit,
) {
    fun retry(lastPlaybackRequest: PlaybackRequest?) {
        lastPlaybackRequest?.let { failedRequest ->
            playbackRequestCoordinator.request(
                episode = failedRequest.episode,
                sourceOverride = failedRequest.source,
                preferredPlayerName = failedRequest.preferredPlayerName,
                preferredQuality = failedRequest.preferredQuality,
            )
        }
    }

    fun dismiss() {
        playbackSession.cancelAndInvalidate()
        playbackSession.clearRouteState()
        playbackEffects.resetPlayerState()
        val backTransition = resolveWatchFlowBackTransition(navigationState(), playbackReturnRoute())
        setNavigationState(backTransition.state)
        setPlaybackReturnRoute(null)
    }
}
