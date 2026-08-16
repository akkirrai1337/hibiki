package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageUrlResolverTest {
    @Test
    fun resolvesProtocolRelativeUrl() {
        assertEquals("https://static.example/poster.webp", "//static.example/poster.webp".toAbsoluteImageUrl())
    }

    @Test
    fun rejectsRelativePath() {
        assertNull("/poster.webp".toAbsoluteImageUrl())
    }
}
