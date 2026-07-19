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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
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
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.LocalizedAppContext
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.design.component.AppFilterBottomSheet
import org.akkirrai.hibiki.core.design.component.AppConnectedToggleFilter
import org.akkirrai.hibiki.core.design.component.AppThreeStateChipFilter
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
    var year by rememberSaveable(initialFilters) {
        mutableStateOf(
            initialFilters.yearFrom
                ?.takeIf { it == initialFilters.yearTo }
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
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surfaceContainerHighest,
                                1f to MaterialTheme.colorScheme.background,
                            )
                        )
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
                            allowExclusion = capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES),
                        )
                    }

                    if (capabilities.supports(AnimeSearchFilter.YEAR_RANGE)) {
                        YearFilter(
                            year = year,
                            yearRange = FILTER_YEAR_RANGE,
                            onYearChange = { year = it },
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
                                year = null
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
                                        yearFrom = year
                                            ?.takeIf { capabilities.supports(AnimeSearchFilter.YEAR_RANGE) },
                                        yearTo = year
                                            ?.takeIf { capabilities.supports(AnimeSearchFilter.YEAR_RANGE) },
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
    year: Int?,
    yearRange: IntRange,
    onYearChange: (Int?) -> Unit,
) {
    CollapsibleRow(
        title = stringResource(R.string.search_filters_year),
        onLongClick = { onYearChange(null) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(year != null) {
                Column {
                    FilterYearPaginator(
                        page = year,
                        pageRange = yearRange,
                        onPageChanged = onYearChange,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = yearRange.first.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = (year ?: yearRange.first).toFloat(),
                    onValueChange = { onYearChange(it.toInt()) },
                    colors = SliderDefaults.colors(
                        activeTrackColor = SliderDefaults.colors().inactiveTrackColor,
                        inactiveTickColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                    ),
                    steps = yearRange.count(),
                    valueRange = yearRange.first.toFloat()..yearRange.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = yearRange.last.toString(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
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
    options: List<SearchFilterOption>,
    included: Set<String>,
    excluded: Set<String>,
    onChange: (Set<String>, Set<String>) -> Unit,
    optionIcon: @Composable ((SearchFilterOption) -> ImageVector?)? = null,
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
    options: List<SearchFilterOption>,
    color: Color,
    icon: ImageVector,
    title: String,
    onClick: (SearchFilterOption) -> Unit,
    modifier: Modifier = Modifier,
    optionIcon: @Composable ((SearchFilterOption) -> ImageVector?)? = null,
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
    var visible by rememberSaveable { mutableStateOf(true) }
    val iconRotation by animateFloatAsState(
        targetValue = if (visible) 0f else -90f,
        label = "filter_arrow",
    )
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { visible = !visible },
                onLongClick = onLongClick,
            )
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                modifier = Modifier
                    .requiredSize(16.dp)
                    .graphicsLayer { rotationZ = iconRotation },
            )
        }
        AnimatedVisibility(visible = visible) { content() }
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
private val IncludedFilterColor = Color(0xFF80DF87)
private val ExcludedFilterColor = Color(0xFFFF9999)
