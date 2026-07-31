package org.akkirrai.hibiki.shared.player

data class PlaybackViewportScale(
    val scaleX: Float,
    val scaleY: Float,
)

fun resolvePlaybackViewportScale(
    mode: VideoScaleMode,
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): PlaybackViewportScale {
    if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) {
        return PlaybackViewportScale(1f, 1f)
    }
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    val viewportAspect = viewportWidth / viewportHeight
    return when (mode) {
        VideoScaleMode.FIT -> PlaybackViewportScale(1f, 1f)
        VideoScaleMode.CROP -> {
            val scale = maxOf(sourceAspect / viewportAspect, viewportAspect / sourceAspect)
            PlaybackViewportScale(scale, scale)
        }
        VideoScaleMode.STRETCH -> PlaybackViewportScale(
            scaleX = if (sourceAspect >= viewportAspect) 1f else viewportAspect / sourceAspect,
            scaleY = if (sourceAspect >= viewportAspect) sourceAspect / viewportAspect else 1f,
        )
    }
}
