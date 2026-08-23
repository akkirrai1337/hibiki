package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.catalog.model.AnimeTypeAlias

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.design.component.filter.AppConnectedToggleFilter
import org.akkirrai.hibiki.design.component.filter.AppFilterExpandIcon
import org.akkirrai.hibiki.design.component.filter.AppFilterSheetActions
import org.akkirrai.hibiki.design.component.filter.AppFilterSheetContentContainer
import org.akkirrai.hibiki.design.component.filter.AppSingleListThreeStateFilter
import org.akkirrai.hibiki.home.model.AppHomeYearFilter
import org.akkirrai.hibiki.home.state.AppHomeFilterCatalogState
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilter
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.catalog.model.AnimeStatus
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

@Composable
fun AppCatalogFilterSheetContent(
    filterCatalog: AnimeCatalogFilterCatalog?,
    isFilterCatalogLoading: Boolean,
    unavailableLabel: String,
    animeType: AnimeTypeAlias?,
    onAnimeTypeChange: (AnimeTypeAlias?) -> Unit,
    pendingFilters: AnimeSearchFilters,
    onPendingFiltersChange: (AnimeSearchFilters) -> Unit,
    includedStatuses: Set<String>,
    onIncludedStatusesChange: (Set<String>) -> Unit,
    yearRange: IntRange,
    defaultYearRange: IntRange,
    onYearRangeChange: (IntRange) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    typeTitle: String,
    genresTitle: String,
    yearTitle: String,
    yearAllLabel: String,
    yearFromLabel: String,
    yearToLabel: String,
    statusTitle: String,
    resetLabel: String,
    applyLabel: String,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    showGenreFilters: Boolean = true,
    maxCollapsedGenreGroups: Int? = 3,
    maxCollapsedGenreItems: Int? = 15,
    modifier: Modifier = Modifier,
) {
    val dropdownIcon = painterResource(R.drawable.animite_drop_down)
    when {
        isFilterCatalogLoading && filterCatalog == null -> AppHomeFilterCatalogState(
            isLoading = true,
            unavailableLabel = unavailableLabel,
            modifier = modifier,
        )
        filterCatalog == null -> AppHomeFilterCatalogState(
            isLoading = false,
            unavailableLabel = unavailableLabel,
            modifier = modifier,
        )
        else -> {
            val catalog = filterCatalog
            val capabilities = catalog.capabilities
            val typeEntries = AnimeTypeAlias.entries.filter { type ->
                catalog.typeOptions.any { it.id.equals(type.alias, ignoreCase = true) }
            }
            AppFilterSheetContentContainer(modifier = modifier) {
                if (capabilities.supports(AnimeCatalogFilter.TYPE) && typeEntries.isNotEmpty()) {
                    AppCatalogTypeFilterSection(
                        title = typeTitle,
                        entries = typeEntries,
                        selected = animeType,
                        onSelected = onAnimeTypeChange,
                        typeIcon = { painterResource(it.iconResource()) },
                        arrowIcon = dropdownIcon,
                    )
                }
                if (
                    showGenreFilters &&
                        catalog.genreOptions.isNotEmpty() &&
                        capabilities.supports(AnimeCatalogFilter.INCLUDED_GENRES)
                ) {
                    AppCatalogGenreFilterSection(
                        title = genresTitle,
                        options = catalog.genreOptions,
                        included = pendingFilters.includedGenreAliases,
                        excluded = pendingFilters.excludedGenreAliases,
                        onChange = { included, excluded ->
                            onPendingFiltersChange(
                                pendingFilters.copy(
                                    includedGenreAliases = included,
                                    excludedGenreAliases = excluded,
                                )
                            )
                        },
                        optionText = optionText,
                        maxCollapsedItems = maxCollapsedGenreItems,
                        maxCollapsedGroups = maxCollapsedGenreGroups,
                        allowExclusion = capabilities.supports(AnimeCatalogFilter.EXCLUDED_GENRES),
                        arrowIcon = dropdownIcon,
                    )
                }
                if (capabilities.supports(AnimeCatalogFilter.YEAR_RANGE)) {
                    AppCatalogYearFilterSection(
                        selectedRange = yearRange,
                        yearRange = defaultYearRange,
                        title = yearTitle,
                        allLabel = yearAllLabel,
                        fromLabel = yearFromLabel,
                        toLabel = yearToLabel,
                        onRangeChange = onYearRangeChange,
                        arrowIcon = dropdownIcon,
                    )
                }
                if (capabilities.supports(AnimeCatalogFilter.STATUS) && catalog.statusOptions.isNotEmpty()) {
                    AppCatalogStatusFilterSection(
                        title = statusTitle,
                        options = catalog.statusOptions,
                        included = includedStatuses,
                        onChange = onIncludedStatusesChange,
                        optionText = optionText,
                        arrowIcon = dropdownIcon,
                    )
                }
                AppFilterSheetActions(
                    resetLabel = resetLabel,
                    applyLabel = applyLabel,
                    resetIcon = painterResource(R.drawable.animite_reset),
                    applyIcon = painterResource(R.drawable.animite_done),
                    onReset = onReset,
                    onApply = onApply,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun AppCatalogGenreFilterSection(
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
        expandIconContent = { expanded, modifier ->
            AppFilterExpandIcon(expanded = expanded, modifier = modifier)
        },
    )
}

@Composable
private fun AppCatalogTypeFilterSection(
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

private fun AnimeTypeAlias.iconResource(): Int = when (this) {
    AnimeTypeAlias.Tv -> R.drawable.animite_tv
    AnimeTypeAlias.Ona -> R.drawable.animite_ona
    AnimeTypeAlias.Ova -> R.drawable.animite_ova
    AnimeTypeAlias.Movie -> R.drawable.animite_movie
}

@Composable
private fun AppCatalogYearFilterSection(
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
private fun AppCatalogStatusFilterSection(
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

private fun AnimeStatus.iconResource(): Int = when (this) {
    AnimeStatus.Finished -> R.drawable.animite_finished
    AnimeStatus.Releasing -> R.drawable.animite_releasing
    AnimeStatus.NotYetReleased -> R.drawable.animite_not_yet_released
    AnimeStatus.Cancelled -> R.drawable.animite_cancelled
    AnimeStatus.Hiatus -> R.drawable.animite_hiatus
}
