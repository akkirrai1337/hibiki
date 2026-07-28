package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.design.component.rememberDeviceScreenTopCornerShape
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.catalog.AnimeStatus
import org.akkirrai.hibiki.shared.catalog.AnimeTypeAlias
import org.akkirrai.hibiki.shared.catalog.AppCatalogFilterSheetContent
import org.akkirrai.hibiki.shared.catalog.applyCatalogFilterDraft
import org.akkirrai.hibiki.shared.home.HomeAction
import org.akkirrai.hibiki.shared.design.component.AppFilterBottomSheet
import org.akkirrai.hibiki.core.design.component.appFilterOptionText
import java.time.Year

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun HomeSearchFiltersSheet(
    onDismissRequest: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    AnimeSearchFiltersSheet(
        initialFilters = state.searchFilters,
        filterCatalog = state.searchFilterCatalog,
        isFilterCatalogLoading = state.isSearchFilterCatalogLoading,
        onApply = { filters ->
            viewModel.dispatch(HomeAction.ApplySearchFilters(filters))
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun AnimeSearchFiltersSheet(
    initialFilters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    isFilterCatalogLoading: Boolean,
    onApply: (AnimeSearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    optionText: @Composable (AnimeCatalogFilterOption) -> String = { appFilterOptionText(it.title) },
    maxCollapsedGenreGroups: Int? = null,
    maxCollapsedGenreItems: Int? = 15,
    showGenreFilters: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val appLanguage = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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
            } ?: FILTER_YEAR_RANGE
        )
    }

    AppFilterBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = rememberDeviceScreenTopCornerShape(),
    ) { sheetContentModifier ->
        LocalizedAppContext(languageMode = appLanguage) {
            AppCatalogFilterSheetContent(
                filterCatalog = filterCatalog,
                isFilterCatalogLoading = isFilterCatalogLoading,
                unavailableLabel = stringResource(R.string.search_filters_unavailable),
                animeType = animeType,
                onAnimeTypeChange = { animeType = it },
                pendingFilters = pendingFilters,
                onPendingFiltersChange = { pendingFilters = it },
                includedStatuses = includedStatuses,
                onIncludedStatusesChange = { includedStatuses = it },
                yearRange = yearRange,
                defaultYearRange = FILTER_YEAR_RANGE,
                onYearRangeChange = { yearRange = it },
                onReset = {
                    pendingFilters = AnimeSearchFilters()
                    animeType = null
                    yearRange = FILTER_YEAR_RANGE
                    includedStatuses = emptySet()
                },
                onApply = {
                    onApply(
                        pendingFilters.applyCatalogFilterDraft(
                            animeType = animeType,
                            includedStatuses = includedStatuses,
                            yearRange = yearRange,
                            defaultYearRange = FILTER_YEAR_RANGE,
                            capabilities = filterCatalog?.capabilities ?: AnimeCatalogCapabilities(),
                            showGenreFilters = showGenreFilters,
                        )
                    )
                    scope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                },
                typeTitle = stringResource(R.string.search_filters_type),
                genresTitle = stringResource(R.string.search_filters_genres),
                yearTitle = stringResource(R.string.search_filters_year),
                yearAllLabel = stringResource(R.string.search_filters_year_all),
                yearFromLabel = stringResource(R.string.search_filters_year_from),
                yearToLabel = stringResource(R.string.search_filters_year_to),
                statusTitle = stringResource(R.string.search_filters_status),
                resetLabel = stringResource(R.string.search_filters_reset),
                applyLabel = stringResource(R.string.search_filters_apply),
                dropdownIcon = ImageVector.vectorResource(R.drawable.animite_drop_down),
                resetIcon = ImageVector.vectorResource(R.drawable.animite_reset),
                applyIcon = ImageVector.vectorResource(R.drawable.animite_done),
                typeIcon = { ImageVector.vectorResource(typeIcon(it)) },
                typeLabel = { typeLabel(it) },
                statusIcon = { statusIcon(it.id) },
                optionText = optionText,
                expandIconContent = { expanded, iconModifier ->
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = iconModifier,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    )
                },
                showGenreFilters = showGenreFilters,
                maxCollapsedGenreGroups = maxCollapsedGenreGroups,
                maxCollapsedGenreItems = maxCollapsedGenreItems,
                modifier = sheetContentModifier,
            )
        }
    }
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun AnimeSearchFiltersSheet(
    initialFilters: AnimeSearchFilters,
    filterCatalog: AnimeSearchFilterCatalog?,
    isFilterCatalogLoading: Boolean,
    onApply: (AnimeSearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    showGenreFilters: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AnimeSearchFiltersSheet(
        initialFilters = initialFilters,
        filterCatalog = filterCatalog?.toSharedCatalog(),
        isFilterCatalogLoading = isFilterCatalogLoading,
        onApply = onApply,
        onDismissRequest = onDismissRequest,
        showGenreFilters = showGenreFilters,
        modifier = modifier,
    )
}


private fun AnimeSearchFilterCatalog.toSharedCatalog(): AnimeCatalogFilterCatalog =
    AnimeCatalogFilterCatalog(
        sortOptions = sortOptions.map(SearchFilterOption::toSharedOption),
        typeOptions = typeOptions.map(SearchFilterOption::toSharedOption),
        statusOptions = statusOptions.map(SearchFilterOption::toSharedOption),
        genreOptions = genreOptions.map(SearchFilterOption::toSharedOption),
        capabilities = AnimeCatalogCapabilities(
            supportedSorts = capabilities.supportedSorts.map { it.name.lowercase() }.toSet(),
            supportedFilters = capabilities.supportedFilters.map { AnimeCatalogFilter.valueOf(it.name) }.toSet(),
        ),
    )

private fun SearchFilterOption.toSharedOption(): AnimeCatalogFilterOption =
    AnimeCatalogFilterOption(id = id, title = title)

@Composable
private fun statusIcon(alias: String): ImageVector {
    val drawable = when (AnimeStatus.fromAlias(alias)) {
        AnimeStatus.Finished -> R.drawable.animite_finished
        AnimeStatus.Releasing -> R.drawable.animite_releasing
        AnimeStatus.NotYetReleased -> R.drawable.animite_not_yet_released
        AnimeStatus.Cancelled -> R.drawable.animite_cancelled
        AnimeStatus.Hiatus -> R.drawable.animite_hiatus
    }
    return ImageVector.vectorResource(drawable)
}

private fun typeLabel(type: AnimeTypeAlias): String = type.alias.uppercase()

private fun typeIcon(type: AnimeTypeAlias): Int = when (type) {
    AnimeTypeAlias.Tv -> R.drawable.animite_tv
    AnimeTypeAlias.Ona -> R.drawable.animite_ona
    AnimeTypeAlias.Ova -> R.drawable.animite_ova
    AnimeTypeAlias.Movie -> R.drawable.animite_movie
}

private val FILTER_YEAR_RANGE = 1940..(Year.now().value + 1)
private val IncludedFilterColor = Color(0xFF80DF87)
private val ExcludedFilterColor = Color(0xFFFF9999)
