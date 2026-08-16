package org.akkirrai.hibiki.shared.details
import org.akkirrai.hibiki.shared.details.data.*
import org.akkirrai.hibiki.shared.details.model.*
import org.akkirrai.hibiki.shared.details.screen.*
import org.akkirrai.hibiki.shared.details.state.*

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
