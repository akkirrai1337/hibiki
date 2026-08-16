package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoScaleModeTest {
    @Test
    fun cyclesThroughModes() {
        assertEquals(VideoScaleMode.CROP, VideoScaleMode.FIT.next())
        assertEquals(VideoScaleMode.STRETCH, VideoScaleMode.CROP.next())
        assertEquals(VideoScaleMode.FIT, VideoScaleMode.STRETCH.next())
    }
}
