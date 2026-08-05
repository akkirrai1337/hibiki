package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.shared.player.model.PlaybackContext
import org.akkirrai.hibiki.shared.player.model.PlaybackRoute
import org.akkirrai.hibiki.shared.player.model.PlaybackStream
import org.akkirrai.hibiki.shared.navigation.AppRoute

internal class HibikiPlaybackSession {
    val requestGeneration: MutableState<Int> = mutableStateOf(0)
    val job: MutableState<Job?> = mutableStateOf(null)
    val activeRoute: MutableState<PlaybackRoute?> = mutableStateOf(null)
    val pendingContext: MutableState<PlaybackContext?> = mutableStateOf(null)
    val returnRoute: MutableState<AppRoute?> = mutableStateOf(null)

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

    fun beginRequest(returnRoute: AppRoute?, context: PlaybackContext) {
        returnRoute?.let { this.returnRoute.value = it }
        pendingContext.value = context
    }

    fun publishPlayback(playback: PlaybackStream, context: PlaybackContext) {
        pendingContext.value = null
        activeRoute.value = PlaybackRoute(playback, context)
    }
}
