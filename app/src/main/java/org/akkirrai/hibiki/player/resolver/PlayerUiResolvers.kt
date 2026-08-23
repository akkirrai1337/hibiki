package org.akkirrai.hibiki.player

import kotlin.math.abs
import org.akkirrai.hibiki.player.model.PlaybackLinkOption
import org.akkirrai.hibiki.text.AppTextKey

val playbackSpeedOptions: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

fun formatWatchSourceEpisodeSummary(
    episodeCount: Int,
    episodeLabel: String,
): String = "· $episodeCount $episodeLabel"

fun formatEpisodeDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${secondsPart(minutes)}:${secondsPart(seconds)}"
}

private fun secondsPart(value: Long): String = value.toString().padStart(2, '0')

fun formatPlaybackSpeed(speed: Float): String = if (speed == 1f) "1x" else "${speed}x"

fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "$hours:${twoDigit(minutes)}:${twoDigit(seconds)}"
    else "${twoDigit(minutes)}:${twoDigit(seconds)}"
}

private fun twoDigit(value: Long): String = value.toString().padStart(2, '0')

private const val PLAYBACK_END_WINDOW_MS = 30_000L
private const val PLAYBACK_END_PERCENT = 5L

fun resolveResumablePlaybackPosition(positionMs: Long, durationMs: Long): Long? {
    val position = positionMs.coerceAtLeast(0L).takeIf { it > 0L } ?: return null
    if (durationMs <= 0L) return position
    val duration = durationMs.coerceAtLeast(1L)
    val resetThresholdMs = maxOf(PLAYBACK_END_WINDOW_MS, duration * PLAYBACK_END_PERCENT / 100L)
    return position.takeIf { duration - position > resetThresholdMs }
}

fun formatSeekDeltaLabel(deltaMs: Long): String {
    val sign = if (deltaMs >= 0L) "+" else "-"
    return sign + formatEpisodeDuration(abs(deltaMs))
}

fun sortQualityLabels(values: List<String>): List<String> = values
    .mapNotNull { it.trim().takeIf(String::isNotBlank) }
    .distinct()
    .sortedByDescending { value -> value.filter(Char::isDigit).toIntOrNull() ?: 0 }

fun uniquePlayerNames(links: List<PlaybackLinkOption>): List<String> = links
    .mapNotNull { it.playerName }
    .distinct()

fun VideoScaleMode.textKey(): AppTextKey = when (this) {
    VideoScaleMode.FIT -> AppTextKey.PlayerVideoScaleFit
    VideoScaleMode.CROP -> AppTextKey.PlayerVideoScaleCrop
    VideoScaleMode.STRETCH -> AppTextKey.PlayerVideoScaleStretch
}

data class VideoScaleFactors(
    val scaleX: Float,
    val scaleY: Float,
)

fun resolveVideoScaleFactors(mode: VideoScaleMode, aspectRatioFactor: Float): VideoScaleFactors = when (mode) {
    VideoScaleMode.FIT -> VideoScaleFactors(
        scaleX = minOf(1f, aspectRatioFactor),
        scaleY = minOf(1f, 1f / aspectRatioFactor),
    )
    VideoScaleMode.CROP -> VideoScaleFactors(
        scaleX = maxOf(1f, aspectRatioFactor),
        scaleY = maxOf(1f, 1f / aspectRatioFactor),
    )
    VideoScaleMode.STRETCH -> VideoScaleFactors(1f, 1f)
}

fun playerToggleValueLocalizationKey(enabled: Boolean): String =
    if (enabled) "watch_player_settings_on" else "watch_player_settings_off"

enum class VideoScaleMode {
    FIT,
    CROP,
    STRETCH;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}
