package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceItemShapeTest {
    @Test
    fun singleItemUsesFullyRoundedShape() {
        assertEquals(RoundedCornerShape(24.dp), sourceItemShape(index = 0, count = 1))
    }

    @Test
    fun firstItemKeepsRoundedTopAndTightBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp,
            ),
            sourceItemShape(index = 0, count = 3),
        )
    }

    @Test
    fun middleItemUsesTightCorners() {
        assertEquals(RoundedCornerShape(8.dp), sourceItemShape(index = 1, count = 3))
    }

    @Test
    fun lastItemKeepsTightTopAndRoundedBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp,
            ),
            sourceItemShape(index = 2, count = 3),
        )
    }
}
