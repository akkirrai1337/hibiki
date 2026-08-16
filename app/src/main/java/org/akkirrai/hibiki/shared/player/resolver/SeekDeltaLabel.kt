package org.akkirrai.hibiki.shared.player

import kotlin.math.abs

fun formatSeekDeltaLabel(deltaMs: Long): String {
    val sign = if (deltaMs >= 0L) "+" else "-"
    return sign + formatEpisodeDuration(abs(deltaMs))
}
