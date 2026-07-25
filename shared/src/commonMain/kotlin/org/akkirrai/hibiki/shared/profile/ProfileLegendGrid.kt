package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ProfileLegendGridItem(val label: String, val valueLabel: String, val color: Color)

@Composable
fun ProfileLegendGrid(
    items: List<ProfileLegendGridItem>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(safeColumns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { item ->
                    ProfileLegendItem(item.label, item.valueLabel, item.color, Modifier.weight(1f))
                }
                repeat((safeColumns - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
