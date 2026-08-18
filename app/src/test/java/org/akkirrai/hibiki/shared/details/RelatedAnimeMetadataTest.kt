package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

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
