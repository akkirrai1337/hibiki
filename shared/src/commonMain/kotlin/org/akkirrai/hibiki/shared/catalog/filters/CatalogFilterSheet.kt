package org.akkirrai.hibiki.shared.catalog.filters

import org.akkirrai.hibiki.shared.catalog.*
import org.akkirrai.hibiki.shared.catalog.model.AnimeTypeAlias

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.design.component.filter.AppFilterBottomSheet
import org.akkirrai.hibiki.shared.design.component.filter.rememberAppFilterBottomSheetState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppCatalogFilterSheet(
    initialFilters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    isFilterCatalogLoading: Boolean,
    onApply: (AnimeSearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    unavailableLabel: String,
    typeTitle: String,
    genresTitle: String,
    yearTitle: String,
    yearAllLabel: String,
    yearFromLabel: String,
    yearToLabel: String,
    statusTitle: String,
    resetLabel: String,
    applyLabel: String,
    defaultYearRange: IntRange,
    optionText: @Composable (AnimeCatalogFilterOption) -> String,
    shape: Shape,
    showGenreFilters: Boolean = true,
    maxCollapsedGenreGroups: Int? = 3,
    maxCollapsedGenreItems: Int? = 15,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberAppFilterBottomSheetState()
    val scope = rememberCoroutineScope()
    var pendingFilters by remember(initialFilters) { mutableStateOf(initialFilters) }
    var animeType by rememberSaveable(initialFilters) {
        mutableStateOf(AnimeTypeAlias.fromAlias(initialFilters.typeAlias))
    }
    var includedStatuses by remember(initialFilters) {
        mutableStateOf(setOfNotNull(initialFilters.statusAlias))
    }
    var yearRange by remember(initialFilters) {
        mutableStateOf(
            initialFilters.yearFrom?.let { from ->
                IntRange(from, initialFilters.yearTo ?: from)
            } ?: defaultYearRange
        )
    }

    AppFilterBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = shape,
    ) { sheetContentModifier ->
        AppCatalogFilterSheetContent(
            filterCatalog = filterCatalog,
            isFilterCatalogLoading = isFilterCatalogLoading,
            unavailableLabel = unavailableLabel,
            animeType = animeType,
            onAnimeTypeChange = { animeType = it },
            pendingFilters = pendingFilters,
            onPendingFiltersChange = { pendingFilters = it },
            includedStatuses = includedStatuses,
            onIncludedStatusesChange = { includedStatuses = it },
            yearRange = yearRange,
            defaultYearRange = defaultYearRange,
            onYearRangeChange = { yearRange = it },
            onReset = {
                pendingFilters = AnimeSearchFilters()
                animeType = null
                yearRange = defaultYearRange
                includedStatuses = emptySet()
            },
            onApply = {
                onApply(
                    pendingFilters.applyCatalogFilterDraft(
                        animeType = animeType,
                        includedStatuses = includedStatuses,
                        yearRange = yearRange,
                        defaultYearRange = defaultYearRange,
                        capabilities = filterCatalog?.capabilities ?: AnimeCatalogCapabilities(),
                        showGenreFilters = showGenreFilters,
                    )
                )
                scope.launch {
                    sheetState.hide()
                    onDismissRequest()
                }
            },
            typeTitle = typeTitle,
            genresTitle = genresTitle,
            yearTitle = yearTitle,
            yearAllLabel = yearAllLabel,
            yearFromLabel = yearFromLabel,
            yearToLabel = yearToLabel,
            statusTitle = statusTitle,
            resetLabel = resetLabel,
            applyLabel = applyLabel,
            optionText = optionText,
            showGenreFilters = showGenreFilters,
            maxCollapsedGenreGroups = maxCollapsedGenreGroups,
            maxCollapsedGenreItems = maxCollapsedGenreItems,
            modifier = sheetContentModifier,
        )
    }
}
