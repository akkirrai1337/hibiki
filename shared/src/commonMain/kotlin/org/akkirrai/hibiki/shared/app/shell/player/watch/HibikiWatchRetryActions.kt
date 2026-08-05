package org.akkirrai.hibiki.shared.app.shell.player.watch

import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.PlaybackRequest

internal class HibikiWatchRetryActions(
    private val lastPlaybackRequest: () -> PlaybackRequest?,
    private val hasPlaybackError: () -> Boolean,
    private val selectedWatchSource: () -> WatchSource?,
    private val retryPlayback: (PlaybackRequest) -> Unit,
    private val retryWatchSources: () -> Unit,
    private val retryEpisodes: () -> Unit,
) {
    val onRetry: () -> Unit = {
        val failedRequest = lastPlaybackRequest()
        if (hasPlaybackError() && failedRequest != null) {
            retryPlayback(failedRequest)
        } else if (selectedWatchSource() == null) {
            retryWatchSources()
        } else {
            retryEpisodes()
        }
    }
}
