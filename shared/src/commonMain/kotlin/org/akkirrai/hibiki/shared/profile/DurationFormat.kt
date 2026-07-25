package org.akkirrai.hibiki.shared.profile

import kotlin.math.round

fun formatDurationHours(durationMs: Long): String {
    if (durationMs <= 0L) return "0"
    val tenths = round(durationMs / 3_600_000.0 * 10.0).toLong()
    return "${tenths / 10L}.${tenths % 10L}"
}
