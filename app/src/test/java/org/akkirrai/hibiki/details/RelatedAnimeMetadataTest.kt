package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals

class RelatedAnimeMetadataTest {
    @Test
    fun formatsYearAndNormalizedType() {
        assertEquals("2024 • TV SERIES", formatRelatedAnimeMetadata(2024, "tv-series"))
    }

    @Test
    fun usesAnnouncementLabelWithoutYear() {
        assertEquals("Announced • MOVIE", formatRelatedAnimeMetadata(null, "movie", "announced", "Announced"))
    }
}
