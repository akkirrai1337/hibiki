package org.akkirrai.hibiki.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.design.component.filter.AppFilterBottomSheet
import org.akkirrai.hibiki.core.design.component.filter.AppConnectedToggleFilter
import org.akkirrai.hibiki.core.design.component.filter.AppCollapsibleFilterSection
import org.akkirrai.hibiki.core.design.component.filter.AppThreeStateChipFilter
import org.akkirrai.hibiki.core.design.component.filter.appFilterOptionText
import java.time.Year
import kotlin.math.roundToInt

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
        onApply = viewModel::applySearchFilters,
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
    filterCatalog: AnimeSearchFilterCatalog?,
    isFilterCatalogLoading: Boolean,
    onApply: (AnimeSearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLanguage = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var pendingFilters by remember(initialFilters) { mutableStateOf(initialFilters) }
    var animeType by rememberSaveable(initialFilters) {
        mutableStateOf(FilterAnimeType.fromAlias(initialFilters.typeAlias))
    }
    var includedStatuses by remember(initialFilters) {
        mutableStateOf(setOfNotNull(initialFilters.statusAlias))
    }
    var yearRange by remember(initialFilters) {
        mutableStateOf(
            initialFilters.toYearRange()
        )
    }

    AppFilterBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) { sheetContentModifier ->
        LocalizedAppContext(languageMode = appLanguage) {
            when {
            isFilterCatalogLoading && filterCatalog == null -> {
                Box(
                    modifier = sheetContentModifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            filterCatalog == null -> {
                Box(
                    modifier = sheetContentModifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_filters_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val catalog = filterCatalog
                val capabilities = catalog.capabilities
                val typeEntries = FilterAnimeType.entries.filter { type ->
                    catalog.typeOptions.any { it.id.equals(type.alias, ignoreCase = true) }
                }
                Column(
                    modifier = sheetContentModifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                ) {
                    if (capabilities.supports(AnimeSearchFilter.TYPE) && typeEntries.isNotEmpty()) {
                        AppConnectedToggleFilter(
                            title = stringResource(R.string.search_filters_type),
                            entries = typeEntries,
                            selected = animeType,
                            onSelected = { animeType = it },
                            icon = { ImageVector.vectorResource(it.iconRes) },
                            text = { it.label },
                        )
                    }

                    if (
                        catalog.genreOptions.isNotEmpty() &&
                        capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES)
                    ) {
                        AppThreeStateChipFilter(
                            title = stringResource(R.string.search_filters_genres),
                            options = catalog.genreOptions,
                            included = pendingFilters.includedGenreAliases,
                            excluded = pendingFilters.excludedGenreAliases,
                            onChange = { included, excluded ->
                                pendingFilters = pendingFilters.copy(
                                    includedGenreAliases = included,
                                    excludedGenreAliases = excluded,
                                )
                            },
                            id = { it.id },
                            text = { appFilterOptionText(it.title) },
                            maxCollapsedItems = 15,
                            maxCollapsedGroups = 3,
                            allowExclusion = capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES),
                            optionSortKey = { it.title },
                            groupByFirstLetter = true,
                        )
                    }

                    if (capabilities.supports(AnimeSearchFilter.YEAR_RANGE)) {
                        YearFilter(
                            selectedRange = yearRange,
                            yearRange = FILTER_YEAR_RANGE,
                            onRangeChange = { yearRange = it },
                        )
                    }

                    if (capabilities.supports(AnimeSearchFilter.STATUS) && catalog.statusOptions.isNotEmpty()) {
                        AppThreeStateChipFilter(
                            title = stringResource(R.string.search_filters_status),
                            options = catalog.statusOptions,
                            included = includedStatuses,
                            excluded = emptySet(),
                            onChange = { included, _ -> includedStatuses = included },
                            id = { it.id },
                            text = { appFilterOptionText(it.title) },
                            optionIcon = { statusIcon(it.id) },
                            allowExclusion = false,
                            optionSortKey = { it.title },
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))
                    FlowRow(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                pendingFilters = AnimeSearchFilters()
                                animeType = null
                                yearRange = FILTER_YEAR_RANGE
                                includedStatuses = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.animite_reset),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.search_filters_reset),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(
                            onClick = {
                                onApply(
                                    pendingFilters.copy(
                                        typeAlias = animeType?.alias
                                            ?.takeIf { capabilities.supports(AnimeSearchFilter.TYPE) },
                                        statusAlias = includedStatuses.firstOrNull()
                                            ?.takeIf { capabilities.supports(AnimeSearchFilter.STATUS) },
                                        includedGenreAliases = pendingFilters.includedGenreAliases
                                            .takeIf { capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES) }
                                            .orEmpty(),
                                        excludedGenreAliases = pendingFilters.excludedGenreAliases
                                            .takeIf { capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES) }
                                            .orEmpty(),
                                        yearFrom = yearRange.first.takeIf {
                                            capabilities.supports(AnimeSearchFilter.YEAR_RANGE) && yearRange != FILTER_YEAR_RANGE
                                        },
                                        yearTo = yearRange.last.takeIf {
                                            capabilities.supports(AnimeSearchFilter.YEAR_RANGE) && yearRange != FILTER_YEAR_RANGE
                                        },
                                    )
                                )
                                scope.launch {
                                    sheetState.hide()
                                    onDismissRequest()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.animite_done),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.search_filters_apply),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun YearFilter(
    selectedRange: IntRange,
    yearRange: IntRange,
    onRangeChange: (IntRange) -> Unit,
) {
    var sliderPosition by remember { mutableStateOf(selectedRange.first.toFloat()..selectedRange.last.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(selectedRange) {
        if (!isDragging) sliderPosition = selectedRange.first.toFloat()..selectedRange.last.toFloat()
    }
    AppCollapsibleFilterSection(
        title = stringResource(R.string.search_filters_year),
        onLongClick = { onRangeChange(yearRange) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (selectedRange == yearRange) {
                Text(
                    text = stringResource(R.string.search_filters_year_all),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stringResource(R.string.search_filters_year_from)} ${selectedRange.first}", fontWeight = FontWeight.SemiBold)
                    Text("${stringResource(R.string.search_filters_year_to)} ${selectedRange.last}", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = yearRange.first.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                )
                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { range ->
                        isDragging = true
                        sliderPosition = range
                        onRangeChange(range.start.roundToInt()..range.endInclusive.roundToInt())
                    },
                    onValueChangeFinished = { isDragging = false },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        inactiveTickColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                    ),
                    steps = (yearRange.count() - 2).coerceAtLeast(0),
                    valueRange = yearRange.first.toFloat()..yearRange.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = yearRange.last.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@Composable
private fun statusIcon(alias: String): ImageVector {
    val drawable = when (alias.trim().lowercase()) {
        "released", "finished", "completed" -> R.drawable.animite_finished
        "ongoing", "releasing", "airing" -> R.drawable.animite_releasing
        "announced", "not_yet_released", "not-yet-released" -> R.drawable.animite_not_yet_released
        "cancelled", "canceled" -> R.drawable.animite_cancelled
        "hiatus", "paused" -> R.drawable.animite_hiatus
        else -> R.drawable.animite_finished
    }
    return ImageVector.vectorResource(drawable)
}

private enum class FilterAnimeType(
    val alias: String,
    val label: String,
    val iconRes: Int,
) {
    Tv("tv", "TV", R.drawable.animite_tv),
    Ona("ona", "ONA", R.drawable.animite_ona),
    Ova("ova", "OVA", R.drawable.animite_ova),
    Movie("movie", "MOVIE", R.drawable.animite_movie);

    companion object {
        fun fromAlias(alias: String?): FilterAnimeType? = entries
            .firstOrNull { it.alias == alias?.trim()?.lowercase() }
    }
}

private val FILTER_YEAR_RANGE = 1940..(Year.now().value + 1)

private fun AnimeSearchFilters.toYearRange(): IntRange {
    val from = yearFrom?.coerceIn(FILTER_YEAR_RANGE) ?: FILTER_YEAR_RANGE.first
    val to = yearTo?.coerceIn(FILTER_YEAR_RANGE) ?: FILTER_YEAR_RANGE.last
    return minOf(from, to)..maxOf(from, to)
}
