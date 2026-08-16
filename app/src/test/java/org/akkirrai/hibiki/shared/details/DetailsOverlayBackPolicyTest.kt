package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsOverlayBackPolicyTest {
    @Test
    fun backPrefersLibraryThenTitleThenPoster() {
        assertEquals(
            DetailsOverlayBackTarget.Library,
            detailsOverlayBackTarget(true, true, true),
        )
        assertEquals(
            DetailsOverlayBackTarget.Title,
            detailsOverlayBackTarget(false, true, true),
        )
        assertEquals(
            DetailsOverlayBackTarget.Poster,
            detailsOverlayBackTarget(false, false, true),
        )
        assertEquals(
            DetailsOverlayBackTarget.None,
            detailsOverlayBackTarget(false, false, false),
        )
    }
}
