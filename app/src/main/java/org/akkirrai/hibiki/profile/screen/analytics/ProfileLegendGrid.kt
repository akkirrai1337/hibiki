package org.akkirrai.hibiki.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

data class ProfileLegendGridItem(val label: String, val valueLabel: String, val color: Color)

@Composable
fun ProfileLegendGrid(
    items: List<ProfileLegendGridItem>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ProfileLegendGridRowGap)) {
        items.chunked(safeColumns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ProfileLegendGridColumnGap)) {
                row.forEach { item ->
                    ProfileLegendItem(item.label, item.valueLabel, item.color, Modifier.weight(1f))
                }
                repeat((safeColumns - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ProfileLegendItem(
    label: String,
    valueLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ProfileLegendItemGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ProfileLegendMarkerSize).clip(CircleShape).background(color))
        Text(label, Modifier.widthIn(max = ProfileLegendLabelMaxWidth), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valueLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
