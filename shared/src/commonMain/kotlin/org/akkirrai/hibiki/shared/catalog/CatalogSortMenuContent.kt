package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AppCatalogSortMenuContent(
    title: String,
    sorts: List<CatalogSort>,
    selectedSort: CatalogSort,
    label: (CatalogSort) -> String,
    icon: (CatalogSort) -> ImageVector,
    expanded: Boolean,
    onSortSelected: (CatalogSort) -> Unit,
    orderContent: @Composable (Boolean, Modifier) -> Unit,
) {
    Text(
        text = title,
        fontSize = CatalogSortMenuTitleFontSize,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .padding(top = CatalogSortMenuTitleTopPadding)
            .fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    sorts.forEach { sort ->
        AppCatalogSortMenuItem(
            icon = icon(sort),
            label = label(sort),
            selected = sort == selectedSort,
            onClick = { onSortSelected(sort) },
            orderContent = { orderModifier -> orderContent(expanded, orderModifier) },
        )
    }
}
