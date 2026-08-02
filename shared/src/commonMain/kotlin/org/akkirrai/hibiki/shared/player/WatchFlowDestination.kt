package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource

sealed interface WatchFlowDestination {
    data object Sources : WatchFlowDestination

    data class Episodes(val source: WatchSource) : WatchFlowDestination
}

/** Returns the previous in-flow destination, or null when the flow itself should close. */
fun WatchFlowDestination.backDestination(): WatchFlowDestination? = when (this) {
    WatchFlowDestination.Sources -> null
    is WatchFlowDestination.Episodes -> WatchFlowDestination.Sources
}
