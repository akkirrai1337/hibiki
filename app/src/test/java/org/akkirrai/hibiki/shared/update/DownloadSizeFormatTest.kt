package org.akkirrai.hibiki.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSizeFormatTest {
    @Test
    fun formatsMegabytesToOneDecimal() {
        assertEquals("1.5 MB", formatDownloadSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("0.0 MB", formatDownloadSize(0))
    }
}
