package org.akkirrai.hibiki.shared.player

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchScreenScaffoldPaddingTest {
    @Test
    fun matchesAndroidWatchListGeometry() {
        val padding = watchScreenContentPadding(24.dp)

        assertEquals(12.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(68.dp, padding.calculateTopPadding())
        assertEquals(12.dp, padding.calculateBottomPadding())
    }
}
