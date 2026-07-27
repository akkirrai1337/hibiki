package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Returns the shape for an item in a grouped source list.
 *
 * Keeping this in commonMain makes the grouping geometry identical on every
 * Compose target.
 */
fun sourceItemShape(index: Int, count: Int): RoundedCornerShape = when {
    count == 1 -> RoundedCornerShape(SourceItemOuterCornerRadius)
    index == 0 -> RoundedCornerShape(
        topStart = SourceItemOuterCornerRadius,
        topEnd = SourceItemOuterCornerRadius,
        bottomStart = 6.dp,
        bottomEnd = 6.dp,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = SourceItemOuterCornerRadius,
        bottomEnd = SourceItemOuterCornerRadius,
    )
    else -> RoundedCornerShape(6.dp)
}
