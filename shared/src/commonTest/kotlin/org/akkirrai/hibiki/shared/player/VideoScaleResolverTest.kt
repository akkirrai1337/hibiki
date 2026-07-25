package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoScaleResolverTest {
    @Test
    fun fitAndCropPreserveAspectRatioWithinBounds() {
        assertEquals(VideoScaleFactors(1f, 0.5f), resolveVideoScaleFactors(VideoScaleMode.FIT, 2f))
        assertEquals(VideoScaleFactors(2f, 1f), resolveVideoScaleFactors(VideoScaleMode.CROP, 2f))
    }

    @Test
    fun stretchUsesNeutralFactors() {
        assertEquals(VideoScaleFactors(1f, 1f), resolveVideoScaleFactors(VideoScaleMode.STRETCH, 2f))
    }
}
