package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import org.akkirrai.hibiki.shared.design.component.AppSingleListThreeStateFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption

@Composable
fun AppCatalogGenreFilterSection(
    title: String,
    options: List<AnimeCatalogFilterOption>,
    included: Set<String>,
    excluded: Set<String>,
    onChange: (Set<String>, Set<String>) -> Unit,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    maxCollapsedItems: Int?,
    maxCollapsedGroups: Int?,
    allowExclusion: Boolean,
    arrowIcon: Painter,
    expandIconContent: @Composable (Boolean, Modifier) -> Unit,
) {
    AppSingleListThreeStateFilter(
        title = title,
        options = options,
        included = included,
        excluded = excluded,
        onChange = onChange,
        id = { it.id },
        text = optionText,
        maxCollapsedItems = maxCollapsedItems,
        maxCollapsedGroups = maxCollapsedGroups,
        allowExclusion = allowExclusion,
        optionSortKey = { it.title },
        groupByFirstLetter = true,
        arrowContent = { modifier -> Icon(painter = arrowIcon, contentDescription = null, modifier = modifier) },
        expandIconContent = expandIconContent,
    )
}
