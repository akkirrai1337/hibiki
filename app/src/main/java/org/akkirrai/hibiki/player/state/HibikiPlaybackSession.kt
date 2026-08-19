package org.akkirrai.hibiki.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackRoute
import org.akkirrai.hibiki.player.model.PlaybackStream
import org.akkirrai.hibiki.app.navigation.AppRoute

internal class HibikiPlaybackSession {
    val requestGeneration: MutableState<Int> = mutableStateOf(0)
    val job: MutableState<Job?> = mutableStateOf(null)
    val activeRoute: MutableState<PlaybackRoute?> = mutableStateOf(null)
    val pendingContext: MutableState<PlaybackContext?> = mutableStateOf(null)
    val returnRoute: MutableState<AppRoute?> = mutableStateOf(null)

    // Lets the currently-mounted platform player save its progress/resume frame before
    // teardown() clears its route state. Every call site that exits an active playback screen
    // (system back, dismissing the error overlay, switching tabs, ...) used to hand-roll its own
    // cancelAndInvalidate()+clearRouteState() sequence, so it was easy for one of them to forget
    // this step -- which is exactly what happened with the system-back path.
    private var onExitPlayback: (() -> Unit)? = null

    fun setOnExitPlayback(action: (() -> Unit)?) {
        onExitPlayback = action
    }

    fun cancelJob() {
        job.value?.cancel()
        job.value = null
    }

    fun cancelAndInvalidate() {
        cancelJob()
        requestGeneration.value++
    }

    fun clearRouteState() {
        activeRoute.value = null
        pendingContext.value = null
    }

    /** The single "user is leaving an active/pending playback screen" teardown sequence. */
    fun teardown() {
        onExitPlayback?.invoke()
        cancelAndInvalidate()
        clearRouteState()
    }

    fun beginRequest(returnRoute: AppRoute?, context: PlaybackContext) {
        returnRoute?.let { this.returnRoute.value = it }
        pendingContext.value = context
    }

    fun publishPlayback(playback: PlaybackStream, context: PlaybackContext) {
        pendingContext.value = null
        activeRoute.value = PlaybackRoute(playback, context)
    }
}
