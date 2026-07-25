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
    count == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp,
    )
    else -> RoundedCornerShape(8.dp)
}
