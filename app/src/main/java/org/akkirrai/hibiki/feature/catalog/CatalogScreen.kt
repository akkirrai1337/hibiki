package org.akkirrai.hibiki.feature.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.PosterPlaceholder
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter as SharedAnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import kotlinx.coroutines.delay
import me.saket.cascade.CascadeDropdownMenu
import me.saket.cascade.rememberCascadeState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModel.Factory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val legacyFilterCatalog = remember(state.filterCatalog) { state.filterCatalog?.toLegacyCatalog() }
    val selectedSort = state.filters.sortAlias.toCatalogSort()
    val listState = rememberLazyListState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isSortVisible by remember { mutableStateOf(true) }
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val availableSorts = remember(legacyFilterCatalog?.capabilities) {
        legacyFilterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }

    LaunchedEffect(availableSorts, selectedSort) {
        if (selectedSort !in availableSorts) {
            val capabilities = legacyFilterCatalog?.capabilities
            val fallback = availableSorts.firstOrNull { it.searchSort == capabilities?.fallbackSort }
                ?: availableSorts.firstOrNull()
            fallback?.let(viewModel::selectSort)
        }
    }

    LaunchedEffect(state.query) {
        delay(350)
        viewModel.load()
    }
    CatalogPaginationEffect(
        listState = listState,
        state = state,
        onLoadMore = viewModel::loadMore,
    )
    CatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortVisible = it },
    )

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                AppCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            state.error != null && state.items.isEmpty() -> {
                AppMessageState(
                    title = stringResource(R.string.catalog_error_title),
                    message = state.error.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(UiDimens.ScreenPadding),
                    actionLabel = stringResource(R.string.search_retry),
                    onActionClick = viewModel::load,
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = CATALOG_CONTENT_TOP_PADDING,
                        end = UiDimens.ScreenPadding,
                        bottom = bottomContentPadding + UiDimens.ScreenPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    appVerticalAnimeListContent(
                        items = state.items,
                        metaText = { anime -> anime.buildCardMeta(
                                announcementLabel = announcementLabel,
                                movieLabel = movieLabel,
                                maxSubtitleParts = 2,
                                separator = " • ",
                        ) },
                        onAnimeClick = onAnimeClick,
                        trailingIcon = Icons.Outlined.ChevronRight,
                        posterContent = { anime ->
                            PosterImage(
                                primaryUrl = anime.posterUrl,
                                fallbackUrl = anime.posterFallbackUrl,
                                contentDescription = anime.title,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    PosterPlaceholder(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        },
                        posterFooterContent = { anime ->
                            libraryStatusByAnimeId[anime.id]?.let { category ->
                                LibraryStatusPosterFooter(category)
                            }
                        },
                        onItemVisible = viewModel::enrichDescription,
                    )

                    if (state.isLoadingMore) {
                        item(key = "catalog_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    if (state.isLoadingMore && state.error != null) {
                        item(key = "catalog_load_more_error") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = viewModel::loadMore)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = state.error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        AppTopScrim(
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    top = CATALOG_HEADER_TOP_PADDING,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CATALOG_SORT_VERTICAL_GAP),
        ) {
            AppSearchTopBar(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                onClear = { viewModel.updateQuery("") },
                onFilterClick = { isFilterSheetOpen = true },
                modifier = Modifier.zIndex(1f),
            )
            val sortOffsetY by animateDpAsState(
                targetValue = if (isSortVisible) {
                    0.dp
                } else {
                    -(CATALOG_SORT_CONTROL_HEIGHT + CATALOG_SORT_VERTICAL_GAP)
                },
                animationSpec = tween(durationMillis = CATALOG_SORT_ANIMATION_DURATION_MS),
                label = "catalog_sort_offset",
            )
            val sortAlpha by animateFloatAsState(
                targetValue = if (isSortVisible) 1f else 0f,
                animationSpec = tween(durationMillis = CATALOG_SORT_ANIMATION_DURATION_MS),
                label = "catalog_sort_alpha",
            )
            CatalogSortControl(
                selectedSort = selectedSort,
                availableSorts = availableSorts,
                expanded = isSortMenuOpen,
                onExpandedChange = { isSortMenuOpen = it },
                onSortSelected = viewModel::selectSort,
                modifier = Modifier.graphicsLayer {
                    translationY = sortOffsetY.toPx()
                    alpha = sortAlpha
                },
            )
        }
    }

    if (isFilterSheetOpen) {
        AnimeSearchFiltersSheet(
            initialFilters = state.filters,
            filterCatalog = legacyFilterCatalog,
            isFilterCatalogLoading = state.isLoading && legacyFilterCatalog == null,
            onApply = viewModel::applyFilters,
            onDismissRequest = { isFilterSheetOpen = false },
        )
    }
}

@Composable
private fun CatalogPaginationEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    state: AnimeCatalogUiState,
    onLoadMore: () -> Unit,
) {
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearEnd = lastVisibleItem >= layoutInfo.totalItemsCount - CATALOG_SCROLL_THRESHOLD
            isNearEnd &&
                !latestState.isLoading &&
                !latestState.isLoadingMore &&
                latestState.canLoadMore &&
                latestState.error == null
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) onLoadMore()
        }
    }
}

@Composable
private fun CatalogSortVisibilityEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onVisibilityChange: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (currentIndex, currentOffset) ->
            val isScrollingDown = currentIndex > previousIndex ||
                (currentIndex == previousIndex && currentOffset > previousOffset)
            val isScrollingUp = currentIndex < previousIndex ||
                (currentIndex == previousIndex && currentOffset < previousOffset)
            when {
                isScrollingDown -> onVisibilityChange(false)
                isScrollingUp -> onVisibilityChange(true)
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }
}

@Composable
private fun CatalogSortControl(
    selectedSort: CatalogSort,
    availableSorts: List<CatalogSort>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cascadeState = rememberCascadeState()
    val haptic = LocalHapticFeedback.current
    val baseContext = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedContext = remember(baseContext, appLanguage) {
        baseContext.withLanguage(appLanguage)
    }
    val sortLabels = remember(localizedContext, availableSorts) {
        availableSorts.associateWith { sort ->
            localizedContext.getString(sort.labelRes)
        }
    }
    val sortTitle = remember(localizedContext) {
        localizedContext.getString(R.string.catalog_sort_title)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CATALOG_SORT_CONTROL_HEIGHT),
    ) {
        AnimatedContent(
            targetState = selectedSort,
            modifier = Modifier.align(Alignment.Center),
            label = "catalog_sort",
        ) { sort ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                        shape = CircleShape,
                    )
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = sort.icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Text(
                    text = sortLabels[sort].orEmpty(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                CatalogSortOrderIcon(
                    atEnd = expanded,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }

        val layoutDirection = LocalLayoutDirection.current
        val screenWidth = LocalWindowInfo.current.containerSize.width
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthDp = with(density) { screenWidth.toDp() }
        val horizontalInsets = UiDimens.ScreenPadding * 2
        val menuWidth = 196.dp
        val offsetX = (screenWidthDp - horizontalInsets - menuWidth) / 2

        CascadeDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            state = cascadeState,
            offset = DpOffset(
                x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) offsetX else -offsetX,
                y = 4.dp,
            ),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(
                text = sortTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally),
            )
            availableSorts.forEach { sort ->
                val isSelected = sort == selectedSort
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    } else {
                        Color.Transparent
                    },
                    label = "catalog_sort_background",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    label = "catalog_sort_text",
                )
                val iconSize by animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 0.dp,
                    label = "catalog_sort_icon",
                )

                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = sort.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                )
                                Text(sortLabels.getValue(sort))
                            }
                            if (isSelected) {
                                CatalogSortOrderIcon(
                                    atEnd = expanded,
                                    modifier = Modifier.size(iconSize),
                                )
                            }
                        }
                    },
                    colors = MenuDefaults.itemColors(textColor = textColor),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onSortSelected(sort)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(backgroundColor),
                )
            }
        }
    }
}

@Composable
private fun CatalogSortOrderIcon(
    atEnd: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val order = AnimatedImageVector.animatedVectorResource(R.drawable.catalog_sort_order)
    Icon(
        painter = rememberAnimatedVectorPainter(
            animatedImageVector = order,
            atEnd = atEnd,
        ),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

private val CatalogSort.icon: ImageVector
    get() = when (this) {
        CatalogSort.Alphabetical -> Icons.Outlined.SortByAlpha
        CatalogSort.Popular -> Icons.Outlined.Whatshot
        CatalogSort.Updated -> Icons.Outlined.Update
    }

class CatalogViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val presenter = AnimeCatalogPresenter(
        repository = repository,
        scope = viewModelScope,
        pageSize = 50,
    )
    val uiState: StateFlow<AnimeCatalogUiState> = presenter.state

    init {
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect {
                presenter.clear()
                presenter.setFilters(AnimeSearchFilters())
                presenter.setQuery("")
                presenter.loadFilterCatalog()
                presenter.search()
            }
        }
        presenter.loadFilterCatalog()
    }

    fun load() = presenter.search()

    fun updateQuery(query: String) = presenter.setQuery(query)

    fun selectSort(sort: CatalogSort) {
        if (uiState.value.filters.sortAlias.toCatalogSort() == sort) return
        presenter.setFilters(presenter.state.value.filters.copy(sortAlias = sort.alias))
        load()
    }

    fun enrichDescription(anime: Anime) {
        if (!anime.description.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { repository.enrichDescription(anime) }
                .onSuccess { enriched ->
                    if (!enriched.description.isNullOrBlank()) presenter.updateItem(enriched)
                }
        }
    }

    fun applyFilters(filters: AnimeSearchFilters) {
        presenter.setFilters(filters.copy(sortAlias = uiState.value.filters.sortAlias))
        load()
    }

    fun loadMore() = presenter.loadMore()

    override fun onCleared() {
        presenter.close()
        repository.close()
        super.onCleared()
    }

    class Factory(
        private val context: android.content.Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CatalogViewModel(
                repository = CatalogRepository(context.applicationContext),
            ) as T
        }
    }
}

private fun AnimeCatalogFilterCatalog.toLegacyCatalog(): AnimeSearchFilterCatalog {
    val supportedSorts = capabilities.supportedSorts.mapNotNull { alias ->
        when (alias.lowercase()) {
            "relevance" -> AnimeSearchSort.RELEVANCE
            "popular", "rating" -> AnimeSearchSort.RATING
            "alphabetical", "title" -> AnimeSearchSort.TITLE
            "year", "updated" -> AnimeSearchSort.YEAR
            "votes" -> AnimeSearchSort.VOTES
            "views" -> AnimeSearchSort.VIEWS
            "comments" -> AnimeSearchSort.COMMENTS
            else -> null
        }
    }.toSet().ifEmpty { setOf(AnimeSearchSort.RELEVANCE) }
    val supportsUpdated = capabilities.supportedSorts.any { it.equals("updated", ignoreCase = true) }
    val fallbackSort = supportedSorts.firstOrNull() ?: AnimeSearchSort.RELEVANCE

    return AnimeSearchFilterCatalog(
        sortOptions = sortOptions.map { SearchFilterOption(it.id, it.title) },
        typeOptions = typeOptions.map { SearchFilterOption(it.id, it.title) },
        statusOptions = statusOptions.map { SearchFilterOption(it.id, it.title) },
        genreOptions = genreOptions.map { SearchFilterOption(it.id, it.title) },
        capabilities = CatalogCapabilities(
            supportedSorts = supportedSorts,
            supportedFilters = capabilities.supportedFilters.mapNotNull { filter ->
                when (filter) {
                    SharedAnimeCatalogFilter.TYPE -> AnimeSearchFilter.TYPE
                    SharedAnimeCatalogFilter.STATUS -> AnimeSearchFilter.STATUS
                    SharedAnimeCatalogFilter.INCLUDED_GENRES -> AnimeSearchFilter.INCLUDED_GENRES
                    SharedAnimeCatalogFilter.EXCLUDED_GENRES -> AnimeSearchFilter.EXCLUDED_GENRES
                    SharedAnimeCatalogFilter.YEAR_RANGE -> AnimeSearchFilter.YEAR_RANGE
                }
            }.toSet(),
            features = if (supportsUpdated) setOf(CatalogFeature.LATEST_RELEASES) else emptySet(),
            fallbackSort = fallbackSort,
        ),
    )
}

enum class CatalogSort(@androidx.annotation.StringRes val labelRes: Int) {
    Alphabetical(R.string.catalog_sort_alphabetical),
    Popular(R.string.catalog_sort_popular),
    Updated(R.string.catalog_sort_updated),
}

private val CatalogSort.alias: String
    get() = when (this) {
        CatalogSort.Alphabetical -> "alphabetical"
        CatalogSort.Popular -> "popular"
        CatalogSort.Updated -> "updated"
    }

private fun String.toCatalogSort(): CatalogSort = when (lowercase()) {
    "alphabetical", "title" -> CatalogSort.Alphabetical
    "updated", "latest", "latest_releases" -> CatalogSort.Updated
    else -> CatalogSort.Popular
}

private val CatalogSort.searchSort: AnimeSearchSort
    get() = when (this) {
        CatalogSort.Alphabetical -> AnimeSearchSort.TITLE
        CatalogSort.Popular -> AnimeSearchSort.RATING
        CatalogSort.Updated -> AnimeSearchSort.RELEVANCE
    }

private fun availableCatalogSorts(
    capabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
): List<CatalogSort> {
    return CatalogSort.entries.filter { sort ->
        when (sort) {
            CatalogSort.Updated -> CatalogFeature.LATEST_RELEASES in capabilities.features
            else -> capabilities.supports(sort.searchSort)
        }
    }
}

private val CATALOG_HEADER_TOP_PADDING = UiDimens.SearchBarTopPadding
private val CATALOG_SEARCH_BAR_HEIGHT = UiDimens.SearchBarHeight
private val CATALOG_SORT_VERTICAL_GAP = 8.dp
private val CATALOG_SORT_CONTROL_HEIGHT = 28.dp
private val CATALOG_CONTENT_TOP_PADDING = CATALOG_HEADER_TOP_PADDING +
    CATALOG_SEARCH_BAR_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP +
    CATALOG_SORT_CONTROL_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP
private const val CATALOG_SORT_ANIMATION_DURATION_MS = 220
private const val CATALOG_SCROLL_THRESHOLD = 3
