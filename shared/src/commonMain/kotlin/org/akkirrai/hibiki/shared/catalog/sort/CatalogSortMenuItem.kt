package org.akkirrai.hibiki.shared.catalog.sort

import org.akkirrai.hibiki.shared.catalog.screen.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppCatalogSortMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    orderContent: @Composable (Modifier) -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        } else {
            Color.Transparent
        },
        label = "catalog_sort_background",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "catalog_sort_text",
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) CatalogSortMenuSelectedIconSize else CatalogSortMenuItemZeroIconSize,
        label = "catalog_sort_icon",
    )

    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CatalogSortMenuContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                    Text(label)
                }
                if (selected) {
                    orderContent(Modifier.size(iconSize))
                }
            }
        },
        colors = MenuDefaults.itemColors(textColor = textColor),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CatalogSortMenuContentHorizontalPadding, vertical = CatalogSortMenuContentVerticalPadding),
        modifier = Modifier
            .padding(CatalogSortMenuItemPadding)
            .clip(RoundedCornerShape(CatalogSortMenuItemCornerRadius))
            .background(backgroundColor),
    )
}
