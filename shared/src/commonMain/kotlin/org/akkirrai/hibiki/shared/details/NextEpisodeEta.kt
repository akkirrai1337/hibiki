package org.akkirrai.hibiki.shared.details

import androidx.compose.runtime.Composable

@Composable
fun formatNextEpisodeEta(
    deltaSeconds: Long,
    daysHoursLabel: @Composable (days: Long, hours: Long) -> String,
    hoursMinutesSecondsLabel: @Composable (hours: Long, minutes: Long, seconds: Long) -> String,
    minutesSecondsLabel: @Composable (minutes: Long, seconds: Long) -> String,
): String? {
    if (deltaSeconds <= 0L) return null

    val days = deltaSeconds / 86_400L
    val hours = deltaSeconds % 86_400L / 3_600L
    val minutes = deltaSeconds % 3_600L / 60L
    val seconds = deltaSeconds % 60L

    return when {
        days > 0L -> daysHoursLabel(days, hours.coerceAtLeast(0L))
        hours > 0L -> hoursMinutesSecondsLabel(
            hours,
            minutes.coerceAtLeast(0L),
            seconds.coerceAtLeast(0L),
        )
        else -> minutesSecondsLabel(minutes.coerceAtLeast(0L), seconds.coerceAtLeast(0L))
    }
}
