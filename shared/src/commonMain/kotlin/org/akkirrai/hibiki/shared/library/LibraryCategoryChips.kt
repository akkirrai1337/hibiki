package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

@Composable
fun <T> AppLibraryCategoryChips(
    selected: T,
    categories: List<T>,
    counts: Map<T, Int>,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LibraryCategoryChipsItemGap),
        contentPadding = PaddingValues(horizontal = LibraryCategoryChipsHorizontalPadding),
    ) {
        items(categories) { category ->
            val isSelected = category == selected
            val count = counts[category] ?: 0
            Surface(
                modifier = Modifier,
                onClick = { onSelected(category) },
                shape = RoundedCornerShape(LibraryCategoryChipCornerRadius),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(LibraryCategoryChipBorderWidth, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
            ) {
                Row(Modifier.padding(horizontal = LibraryCategoryChipHorizontalPadding, vertical = LibraryCategoryChipVerticalPadding), horizontalArrangement = Arrangement.spacedBy(LibraryCategoryChipContentGap), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(category), null, Modifier.size(LibraryCategoryChipIconSize))
                    Text(if (count > 0) "${label(category)} $count" else label(category), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
