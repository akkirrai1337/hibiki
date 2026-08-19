package org.akkirrai.hibiki.details.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun rememberNextEpisodeEta(
    nextEpisodeAt: Long?,
    nowEpochSeconds: () -> Long,
    daysHoursLabel: @Composable (days: Long, hours: Long) -> String,
    hoursMinutesSecondsLabel: @Composable (hours: Long, minutes: Long, seconds: Long) -> String,
    minutesSecondsLabel: @Composable (minutes: Long, seconds: Long) -> String,
): String? {
    val seconds = nextEpisodeAt?.takeIf { it > 0L } ?: return null
    var nowSeconds by remember(seconds) { mutableLongStateOf(nowEpochSeconds()) }

    LaunchedEffect(seconds) {
        while (nowSeconds < seconds) {
            nowSeconds = nowEpochSeconds()
            if (nowSeconds >= seconds) break
            delay(1_000L)
        }
    }

    return formatNextEpisodeEta(
        deltaSeconds = seconds - nowSeconds,
        daysHoursLabel = daysHoursLabel,
        hoursMinutesSecondsLabel = hoursMinutesSecondsLabel,
        minutesSecondsLabel = minutesSecondsLabel,
    )
}

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
