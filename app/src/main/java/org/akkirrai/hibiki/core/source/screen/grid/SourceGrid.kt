package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T> AppSourceGrid(
    items: List<T>,
    emptyContent: @Composable () -> Unit,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (items.isEmpty()) {
        emptyContent()
    } else {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(SourceGridColumnSpacing)) {
                rowItems.forEach { item ->
                    itemContent(item, Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
