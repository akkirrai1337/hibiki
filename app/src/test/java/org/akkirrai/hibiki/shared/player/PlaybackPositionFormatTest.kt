package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackPositionFormatTest {
    @Test
    fun formatsMinutesAndHours() {
        assertEquals("00:05", formatPlaybackPosition(5_000L))
        assertEquals("1:02:03", formatPlaybackPosition(3_723_000L))
        assertEquals("00:00", formatPlaybackPosition(-1L))
    }
}
