package org.akkirrai.hibiki.shared.player

const val DefaultSkipSegmentCountdownSeconds = 10

/** Common state for the opening/ending skip prompt across media hosts. */
data class PlayerSkipState(
    val hiddenSegmentKey: String? = null,
    val countdownSeconds: Int = DefaultSkipSegmentCountdownSeconds,
) {
    fun resetCountdown(): PlayerSkipState = copy(
        countdownSeconds = DefaultSkipSegmentCountdownSeconds,
    )

    fun tick(): PlayerSkipState = copy(
        countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0),
    )

    fun hide(segmentKey: String): PlayerSkipState = copy(hiddenSegmentKey = segmentKey)
}
