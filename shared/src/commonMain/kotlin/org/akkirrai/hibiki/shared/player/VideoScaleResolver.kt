package org.akkirrai.hibiki.shared.player

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
