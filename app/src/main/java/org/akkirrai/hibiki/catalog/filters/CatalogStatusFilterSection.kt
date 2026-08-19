package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.catalog.model.AnimeStatus

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.design.component.filter.AppFilterExpandIcon
import org.akkirrai.hibiki.design.component.filter.AppSingleListThreeStateFilter
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterOption

@Composable
fun AppCatalogStatusFilterSection(
    title: String,
    options: List<AnimeCatalogFilterOption>,
    included: Set<String>,
    onChange: (Set<String>) -> Unit,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    arrowIcon: Painter,
) {
    AppSingleListThreeStateFilter(
        title = title,
        options = options,
        included = included,
        excluded = emptySet(),
        onChange = { nextIncluded, _ -> onChange(nextIncluded) },
        id = { it.id },
        text = optionText,
        optionIcon = { painterResource(AnimeStatus.fromAlias(it.id).iconResource()) },
        allowExclusion = false,
        optionSortKey = { it.title },
        arrowContent = { modifier -> Icon(painter = arrowIcon, contentDescription = null, modifier = modifier) },
        expandIconContent = { expanded, modifier ->
            AppFilterExpandIcon(expanded = expanded, modifier = modifier)
        },
    )
}
