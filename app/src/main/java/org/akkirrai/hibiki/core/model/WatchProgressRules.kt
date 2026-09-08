package org.akkirrai.hibiki.core.model

import org.akkirrai.hibiki.app.settings.AppPreferences

/**
 * The rules and labels that describe watch progress, in one place.
 *
 * Each of these used to live as a private copy inside whichever screen needed it, which is how the
 * episode list and the watch statistics ended up disagreeing about what "watched" even means (a
 * percentage of the duration in one, "within a second of the very end" in the other), and how the
 * same h:mm:ss formatter came to exist in four files under three different names.
 */

/**
 * Whether a position counts as having finished the episode. [thresholdPercent] defaults to the
 * user's own setting; it is a parameter so the rule itself stays testable without preferences.
 */
fun isEpisodeWatched(
    positionMs: Long,
    durationMs: Long,
    thresholdPercent: Int = AppPreferences.watchedThresholdPercent,
): Boolean = durationMs > 0L && positionMs >= durationMs * thresholdPercent / 100L

fun EpisodeWatchProgress.isWatchedToEnd(
    thresholdPercent: Int = AppPreferences.watchedThresholdPercent,
): Boolean = isEpisodeWatched(positionMs, durationMs, thresholdPercent)

/**
 * A playback timestamp: `mm:ss`, widening to `h:mm:ss` only once there is an hour to show. Used for
 * both a position and a duration - they are the same kind of value and always appear side by side.
 */
fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** Episode numbers are Doubles because of half-episodes (12.5); whole ones must not read "12.0". */
fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
