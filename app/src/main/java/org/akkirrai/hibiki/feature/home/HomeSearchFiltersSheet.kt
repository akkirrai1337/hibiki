package org.akkirrai.hibiki.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.design.component.AppThreeStateChipFilter
import org.akkirrai.hibiki.core.design.component.rememberDeviceScreenTopCornerShape
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.shared.catalog.AnimeStatus
import org.akkirrai.hibiki.shared.catalog.AnimeTypeAlias
import org.akkirrai.hibiki.shared.home.HomeAction
import org.akkirrai.hibiki.shared.design.component.AppFilterBottomSheet
import org.akkirrai.hibiki.shared.design.component.AppCollapsibleFilterSection
import org.akkirrai.hibiki.shared.design.component.AppConnectedToggleFilter
import org.akkirrai.hibiki.shared.home.AppHomeYearFilter
import org.akkirrai.hibiki.shared.home.AppHomeFilterCatalogState
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
            when {
            isFilterCatalogLoading && filterCatalog == null -> {
                AppHomeFilterCatalogState(
                    isLoading = true,
                    unavailableLabel = stringResource(R.string.search_filters_unavailable),
                    modifier = sheetContentModifier,
                )
            }

            filterCatalog == null -> {
                AppHomeFilterCatalogState(
                    isLoading = false,
                    unavailableLabel = stringResource(R.string.search_filters_unavailable),
                    modifier = sheetContentModifier,
                )
            }

            else -> {
                val catalog = filterCatalog
                val capabilities = catalog.capabilities
                val typeEntries = AnimeTypeAlias.entries.filter { type ->
                    catalog.typeOptions.any { it.id.equals(type.alias, ignoreCase = true) }
                }
                Column(
                    modifier = sheetContentModifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                ) {
                    if (capabilities.supports(AnimeCatalogFilter.TYPE) && typeEntries.isNotEmpty()) {
                        AppConnectedToggleFilter(
                            title = stringResource(R.string.search_filters_type),
                            entries = typeEntries,
                            selected = animeType,
                            onSelected = { animeType = it },
                            arrowContent = { modifier ->
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                                    contentDescription = null,
                                    modifier = modifier,
                                )
                            },
                            allowClearSelection = true,
                            icon = { ImageVector.vectorResource(typeIcon(it)) },
                            text = { typeLabel(it) },
                        )
                    }

                    if (
                        showGenreFilters &&
                        catalog.genreOptions.isNotEmpty() &&
                        capabilities.supports(AnimeCatalogFilter.INCLUDED_GENRES)
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
                            text = optionText,
                            maxCollapsedItems = maxCollapsedGenreItems,
                            maxCollapsedGroups = maxCollapsedGenreGroups,
                            allowExclusion = capabilities.supports(AnimeCatalogFilter.EXCLUDED_GENRES),
                            singleList = true,
                            optionSortKey = { it.title },
                            groupByFirstLetter = true,
                        )
                    }

                    if (capabilities.supports(AnimeCatalogFilter.YEAR_RANGE)) {
                        YearFilter(
                            selectedRange = yearRange,
                            yearRange = FILTER_YEAR_RANGE,
                            onRangeChange = { yearRange = it },
                        )
                    }

                    if (capabilities.supports(AnimeCatalogFilter.STATUS) && catalog.statusOptions.isNotEmpty()) {
                        AppThreeStateChipFilter(
                            title = stringResource(R.string.search_filters_status),
                            options = catalog.statusOptions,
                            included = includedStatuses,
                            excluded = emptySet(),
                            onChange = { included, _ -> includedStatuses = included },
                            id = { it.id },
                            text = optionText,
                            optionIcon = { statusIcon(it.id) },
                            allowExclusion = false,
                            singleList = true,
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
                                            ?.takeIf { capabilities.supports(AnimeCatalogFilter.TYPE) },
                                        statusAlias = includedStatuses.firstOrNull()
                                            ?.takeIf { capabilities.supports(AnimeCatalogFilter.STATUS) },
                                        includedGenreAliases = pendingFilters.includedGenreAliases
                                            .takeIf { showGenreFilters && capabilities.supports(AnimeCatalogFilter.INCLUDED_GENRES) }
                                            .orEmpty(),
                                        excludedGenreAliases = pendingFilters.excludedGenreAliases
                                            .takeIf { showGenreFilters && capabilities.supports(AnimeCatalogFilter.EXCLUDED_GENRES) }
                                            .orEmpty(),
                                        yearFrom = yearRange.first
                                            .takeIf { capabilities.supports(AnimeCatalogFilter.YEAR_RANGE) && yearRange != FILTER_YEAR_RANGE },
                                        yearTo = yearRange.last
                                            .takeIf { capabilities.supports(AnimeCatalogFilter.YEAR_RANGE) && yearRange != FILTER_YEAR_RANGE },
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
private fun YearFilter(
    selectedRange: IntRange,
    yearRange: IntRange,
    onRangeChange: (IntRange) -> Unit,
) {
    AppHomeYearFilter(
        selectedRange = selectedRange,
        yearRange = yearRange,
        title = stringResource(R.string.search_filters_year),
        allLabel = stringResource(R.string.search_filters_year_all),
        fromLabel = stringResource(R.string.search_filters_year_from),
        toLabel = stringResource(R.string.search_filters_year_to),
        onRangeChange = onRangeChange,
        arrowContent = { modifier ->
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                modifier = modifier,
            )
        },
    )
}

@Composable
private fun FilterYearPaginator(
    page: Int?,
    pageRange: IntRange,
    onPageChanged: (Int) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        val screenWidth = LocalWindowInfo.current.containerDpSize.width
        val pageItemSize = if (screenWidth > (56 * 5).dp) 56.dp else screenWidth / 5
        var shortenPage by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.border(
                width = 2.dp,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        ) {
            Text(
                text = if (shortenPage) "000" else "0000",
                color = Color.Transparent,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
            )
        }

        val paginatorState = rememberLazyListState()
        LaunchedEffect(page) {
            page?.let { paginatorState.animateScrollToItem(it - pageRange.first) }
        }
        LazyRow(
            state = paginatorState,
            contentPadding = PaddingValues(horizontal = pageItemSize * 2f),
            userScrollEnabled = false,
            modifier = Modifier.requiredWidth(pageItemSize * 5),
        ) {
            items(pageRange.count()) { index ->
                val currentPage = pageRange.first + index
                val textAlpha by animateFloatAsState(
                    targetValue = if (currentPage == page) 1f else 0.5f,
                    label = "year_page_alpha",
                )
                Box(modifier = Modifier.requiredSize(pageItemSize)) {
                    Text(
                        text = if (shortenPage) {
                            "'${currentPage.toString().takeLast(2)}"
                        } else {
                            currentPage.toString()
                        },
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha),
                        onTextLayout = { if (it.hasVisualOverflow) shortenPage = true },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        Button(
            enabled = (page ?: pageRange.first) > pageRange.first,
            onClick = { onPageChanged((page ?: pageRange.first) - 1) },
            contentPadding = PaddingValues(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.requiredSize(24.dp),
            )
        }
        Button(
            enabled = (page ?: pageRange.first) < pageRange.last,
            onClick = { onPageChanged((page ?: pageRange.first) + 1) },
            contentPadding = PaddingValues(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.requiredSize(24.dp),
            )
        }
    }
}

@Composable
private fun ThreeStateChipFilter(
    title: String,
    options: List<AnimeCatalogFilterOption>,
    included: Set<String>,
    excluded: Set<String>,
    onChange: (Set<String>, Set<String>) -> Unit,
    optionIcon: @Composable ((AnimeCatalogFilterOption) -> ImageVector?)? = null,
    maxCollapsedItems: Int? = null,
) {
    var showAllOptions by rememberSaveable(title) { mutableStateOf(false) }
    CollapsibleRow(
        title = title,
        onLongClick = { onChange(emptySet(), emptySet()) },
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            val includedOptions = options.filter { it.id in included }
            val excludedOptions = options.filter { it.id in excluded }
            val allOptions = options.filterNot { it.id in included || it.id in excluded }
            val visibleAllOptions = if (maxCollapsedItems != null && !showAllOptions) {
                allOptions.take(maxCollapsedItems)
            } else {
                allOptions
            }

            ChipFilterFlowRow(
                options = includedOptions,
                color = IncludedFilterColor,
                icon = Icons.Rounded.AddCircleOutline,
                title = stringResource(R.string.search_filters_include),
                optionIcon = optionIcon,
                onClick = { onChange(included - it.id, excluded + it.id) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ChipFilterFlowRow(
                options = excludedOptions,
                color = ExcludedFilterColor,
                icon = Icons.Rounded.Block,
                title = stringResource(R.string.search_filters_exclude),
                optionIcon = optionIcon,
                onClick = { onChange(included, excluded - it.id) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ChipFilterFlowRow(
                options = visibleAllOptions,
                color = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Rounded.RadioButtonChecked,
                title = stringResource(R.string.search_filters_all),
                optionIcon = optionIcon,
                onClick = { onChange(included + it.id, excluded) },
            )
            if (maxCollapsedItems != null && allOptions.size > maxCollapsedItems) {
                IconButton(
                    onClick = { showAllOptions = !showAllOptions },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = if (showAllOptions) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFilterFlowRow(
    options: List<AnimeCatalogFilterOption>,
    color: Color,
    icon: ImageVector,
    title: String,
    onClick: (AnimeCatalogFilterOption) -> Unit,
    modifier: Modifier = Modifier,
    optionIcon: @Composable ((AnimeCatalogFilterOption) -> ImageVector?)? = null,
) {
    AnimatedContent(targetState = options, label = "filter_chips") { current ->
        if (current.isNotEmpty()) {
            Column {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                FlowRow(
                    modifier = modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    current.forEach { option ->
                        FilterChip(
                            color = color,
                            icon = optionIcon?.invoke(option),
                            text = option.title,
                            onClick = { onClick(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    color: Color,
    icon: ImageVector?,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = {})
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CollapsibleRow(
    title: String,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCollapsibleFilterSection(
        title = title,
        onLongClick = onLongClick,
        arrowContent = { modifier ->
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                modifier = modifier,
            )
        },
        content = content,
    )
}

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
