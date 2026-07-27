package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.AppConnectedToggleFilter

@Composable
fun AppCatalogTypeFilterSection(
    title: String,
    entries: List<AnimeTypeAlias>,
    selected: AnimeTypeAlias?,
    onSelected: (AnimeTypeAlias?) -> Unit,
    typeIcon: @Composable (AnimeTypeAlias) -> ImageVector,
    typeLabel: @Composable (AnimeTypeAlias) -> String,
    arrowContent: @Composable (Modifier) -> Unit,
) {
    AppConnectedToggleFilter(
        title = title,
        entries = entries,
        selected = selected,
        onSelected = onSelected,
        arrowContent = arrowContent,
        allowClearSelection = true,
        icon = typeIcon,
        text = typeLabel,
    )
}
