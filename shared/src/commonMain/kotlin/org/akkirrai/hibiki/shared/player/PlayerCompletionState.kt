package org.akkirrai.hibiki.shared.player

/** Common guard preventing auto-play completion from firing more than once per episode. */
data class PlayerCompletionState(
    val isHandled: Boolean = false,
) {
    fun markHandled(): PlayerCompletionState = copy(isHandled = true)

    fun reset(): PlayerCompletionState = PlayerCompletionState()
}
