package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackViewportScaleTest {
    @Test
    fun fitKeepsTheNativeAspectRatio() {
        assertEquals(
            PlaybackViewportScale(1f, 1f),
            resolvePlaybackViewportScale(VideoScaleMode.FIT, 1920, 1080, 800f, 800f),
        )
    }

    @Test
    fun cropFillsAViewportWithAUniformScale() {
        assertEquals(
            PlaybackViewportScale(1.7777778f, 1.7777778f),
            resolvePlaybackViewportScale(VideoScaleMode.CROP, 1920, 1080, 800f, 800f),
        )
    }

    @Test
    fun stretchUsesIndependentAxes() {
        assertEquals(
            PlaybackViewportScale(1f, 1.7777778f),
            resolvePlaybackViewportScale(VideoScaleMode.STRETCH, 1920, 1080, 800f, 800f),
        )
    }

    @Test
    fun unknownDimensionsKeepTheSurfaceUnscaled() {
        assertEquals(
            PlaybackViewportScale(1f, 1f),
            resolvePlaybackViewportScale(VideoScaleMode.CROP, 0, 0, 800f, 800f),
        )
    }
}
