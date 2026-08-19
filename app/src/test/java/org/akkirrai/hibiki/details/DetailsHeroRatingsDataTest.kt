package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.akkirrai.hibiki.catalog.model.AnimeRating

class DetailsHeroRatingsDataTest {
    @Test
    fun formatsFirstRatingAndViewCount() {
        assertEquals(
            DetailsHeroRatingsData("8.50", "1.2K"),
            resolveDetailsHeroRatings(listOf(AnimeRating("source", 8.5)), 1_200L),
        )
        assertNull(resolveDetailsHeroRatings(emptyList(), 0L))
    }
}
