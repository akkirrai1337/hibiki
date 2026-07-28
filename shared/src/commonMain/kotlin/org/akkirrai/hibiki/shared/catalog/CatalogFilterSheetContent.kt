package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.animite_done
import hibiki.shared.generated.resources.animite_drop_down
import hibiki.shared.generated.resources.animite_reset
import org.akkirrai.hibiki.shared.design.component.AppFilterSheetActions
import org.akkirrai.hibiki.shared.design.component.AppFilterSheetContentContainer
import org.akkirrai.hibiki.shared.home.AppHomeFilterCatalogState
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

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
    typeLabel: @Composable (AnimeTypeAlias) -> String,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    showGenreFilters: Boolean = true,
    maxCollapsedGenreGroups: Int? = null,
    maxCollapsedGenreItems: Int? = 15,
    modifier: Modifier = Modifier,
) {
    val dropdownIcon = painterResource(Res.drawable.animite_drop_down)
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
                        typeLabel = typeLabel,
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
                        optionIcon = { painterResource(AnimeStatus.fromAlias(it.id).iconResource()) },
                        arrowIcon = dropdownIcon,
                    )
                }
                AppFilterSheetActions(
                    resetLabel = resetLabel,
                    applyLabel = applyLabel,
                    resetIcon = painterResource(Res.drawable.animite_reset),
                    applyIcon = painterResource(Res.drawable.animite_done),
                    onReset = onReset,
                    onApply = onApply,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                )
            }
        }
    }
}
