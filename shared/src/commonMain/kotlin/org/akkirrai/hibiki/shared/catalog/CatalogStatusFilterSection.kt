package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.component.AppSingleListThreeStateFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption

@Composable
fun AppCatalogStatusFilterSection(
    title: String,
    options: List<AnimeCatalogFilterOption>,
    included: Set<String>,
    onChange: (Set<String>) -> Unit,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    optionIcon: @Composable (AnimeCatalogFilterOption) -> ImageVector?,
    arrowIcon: ImageVector,
    expandIconContent: @Composable (Boolean, Modifier) -> Unit,
) {
    AppSingleListThreeStateFilter(
        title = title,
        options = options,
        included = included,
        excluded = emptySet(),
        onChange = { nextIncluded, _ -> onChange(nextIncluded) },
        id = { it.id },
        text = optionText,
        optionIcon = optionIcon,
        allowExclusion = false,
        optionSortKey = { it.title },
        arrowContent = { modifier -> Icon(arrowIcon, contentDescription = null, modifier = modifier) },
        expandIconContent = expandIconContent,
    )
}
