package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.painter.Painter
import org.akkirrai.hibiki.shared.design.component.AppConnectedToggleFilter

@Composable
fun AppCatalogTypeFilterSection(
    title: String,
    entries: List<AnimeTypeAlias>,
    selected: AnimeTypeAlias?,
    onSelected: (AnimeTypeAlias?) -> Unit,
    typeIcon: @Composable (AnimeTypeAlias) -> Painter,
    arrowIcon: Painter,
) {
    AppConnectedToggleFilter(
        title = title,
        entries = entries,
        selected = selected,
        onSelected = onSelected,
        arrowContent = { modifier -> Icon(painter = arrowIcon, contentDescription = null, modifier = modifier) },
        allowClearSelection = true,
        icon = typeIcon,
        text = { it.alias.uppercase() },
    )
}
