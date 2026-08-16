package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSpeedFormatTest {
    @Test
    fun formatsPlaybackSpeedWithXSuffix() {
        assertEquals("1x", formatPlaybackSpeed(1f))
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
    }
}
