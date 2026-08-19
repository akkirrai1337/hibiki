package org.akkirrai.hibiki.details
import org.akkirrai.hibiki.details.data.*
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.screen.*
import org.akkirrai.hibiki.details.state.*

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsHeroOverlayBackButtonPaddingTest {
    @Test
    fun matchesAndroidHeroBackButtonGeometry() {
        val padding = detailsHeroOverlayBackButtonPadding(24.dp)

        assertEquals(12.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(32.dp, padding.calculateTopPadding())
    }
}
