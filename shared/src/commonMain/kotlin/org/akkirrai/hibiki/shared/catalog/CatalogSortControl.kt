package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppCatalogSortControl(
    sortKey: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    orderContent: @Composable (Modifier) -> Unit,
    menuContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CatalogSortControlHeight),
    ) {
        AppCatalogSortPill(
            sortKey = sortKey,
            icon = icon,
            label = label,
            onClick = { onExpandedChange(!expanded) },
            orderContent = orderContent,
            modifier = Modifier.align(Alignment.Center),
        )
        menuContent()
    }
}
