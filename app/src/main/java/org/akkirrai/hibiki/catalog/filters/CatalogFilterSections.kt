package org.akkirrai.hibiki.catalog.filters

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.catalog.model.AnimeStatus
import org.akkirrai.hibiki.catalog.model.AnimeTypeAlias
import org.akkirrai.hibiki.design.component.filter.AppConnectedToggleFilter
import org.akkirrai.hibiki.design.component.filter.AppFilterExpandIcon
import org.akkirrai.hibiki.design.component.filter.AppSingleListThreeStateFilter
import org.akkirrai.hibiki.home.model.AppHomeYearFilter

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

fun AnimeTypeAlias.iconResource(): Int = when (this) {
    AnimeTypeAlias.Tv -> R.drawable.animite_tv
    AnimeTypeAlias.Ona -> R.drawable.animite_ona
    AnimeTypeAlias.Ova -> R.drawable.animite_ova
    AnimeTypeAlias.Movie -> R.drawable.animite_movie
}

@Composable
fun AppCatalogYearFilterSection(
    selectedRange: IntRange,
    yearRange: IntRange,
    title: String,
    allLabel: String,
    fromLabel: String,
    toLabel: String,
    onRangeChange: (IntRange) -> Unit,
    arrowIcon: Painter,
) {
    AppHomeYearFilter(
        selectedRange = selectedRange,
        yearRange = yearRange,
        title = title,
        allLabel = allLabel,
        fromLabel = fromLabel,
        toLabel = toLabel,
        onRangeChange = onRangeChange,
        arrowContent = { modifier ->
            Icon(
                painter = arrowIcon,
                contentDescription = null,
                modifier = modifier,
            )
        },
    )
}

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

fun AnimeStatus.iconResource(): Int = when (this) {
    AnimeStatus.Finished -> R.drawable.animite_finished
    AnimeStatus.Releasing -> R.drawable.animite_releasing
    AnimeStatus.NotYetReleased -> R.drawable.animite_not_yet_released
    AnimeStatus.Cancelled -> R.drawable.animite_cancelled
    AnimeStatus.Hiatus -> R.drawable.animite_hiatus
}
