package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceItemShapeTest {
    @Test
    fun singleItemUsesFullyRoundedShape() {
        assertEquals(RoundedCornerShape(SourceItemOuterCornerRadius), sourceItemShape(index = 0, count = 1))
    }

    @Test
    fun firstItemKeepsRoundedTopAndTightBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = SourceItemOuterCornerRadius,
                topEnd = SourceItemOuterCornerRadius,
                bottomStart = SourceItemInnerCornerRadius,
                bottomEnd = SourceItemInnerCornerRadius,
            ),
            sourceItemShape(index = 0, count = 3),
        )
    }

    @Test
    fun middleItemUsesTightCorners() {
        assertEquals(RoundedCornerShape(SourceItemInnerCornerRadius), sourceItemShape(index = 1, count = 3))
    }

    @Test
    fun lastItemKeepsTightTopAndRoundedBottom() {
        assertEquals(
            RoundedCornerShape(
                topStart = SourceItemInnerCornerRadius,
                topEnd = SourceItemInnerCornerRadius,
                bottomStart = SourceItemOuterCornerRadius,
                bottomEnd = SourceItemOuterCornerRadius,
            ),
            sourceItemShape(index = 2, count = 3),
        )
    }
}
