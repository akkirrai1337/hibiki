package org.akkirrai.hibiki.player

import kotlin.test.Test
import kotlin.test.assertEquals

class QualityOptionsResolverTest {
    @Test
    fun trimsDeduplicatesAndSortsQualityLabels() {
        assertEquals(
            listOf("1080p", "720p", "480p", "auto"),
            sortQualityLabels(listOf(" 720p ", "1080p", "auto", "480p", "720p")),
        )
    }
}
