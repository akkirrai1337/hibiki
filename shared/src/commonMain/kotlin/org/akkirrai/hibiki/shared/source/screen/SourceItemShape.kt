package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.shape.RoundedCornerShape

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
        bottomStart = SourceItemInnerCornerRadius,
        bottomEnd = SourceItemInnerCornerRadius,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = SourceItemInnerCornerRadius,
        topEnd = SourceItemInnerCornerRadius,
        bottomStart = SourceItemOuterCornerRadius,
        bottomEnd = SourceItemOuterCornerRadius,
    )
    else -> RoundedCornerShape(SourceItemInnerCornerRadius)
}
