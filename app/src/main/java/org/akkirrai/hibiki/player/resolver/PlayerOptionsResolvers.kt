package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.PlaybackLinkOption
import org.akkirrai.hibiki.text.AppTextKey

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
