package org.akkirrai.hibiki.shared.catalog.sort

import org.akkirrai.hibiki.shared.catalog.screen.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppCatalogSortPill(
    sortKey: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    orderContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CatalogSortControlHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = sortKey,
            label = "catalog_sort",
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = CatalogSortPillHorizontalPadding, vertical = CatalogSortPillVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(CatalogSortPillContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CatalogSortPillIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Text(
                    text = label,
                    fontSize = CatalogSortPillFontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                orderContent(Modifier.size(CatalogSortPillIconSize))
            }
        }
    }
}
