package org.akkirrai.hibiki.feature.catalog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortPill
import org.akkirrai.hibiki.shared.catalog.AppCatalogContentList
import org.akkirrai.hibiki.shared.catalog.AppCatalogTopOverlay
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortMenuItem
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortVisibilityEffect
import org.akkirrai.hibiki.shared.catalog.catalogSortFromAlias
import org.akkirrai.hibiki.shared.catalog.toAlias
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreState
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading
import org.akkirrai.hibiki.shared.design.component.AppMessageState
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder
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
    val selectedSort = catalogSortFromAlias(state.filters.sortAlias)
    val selectedSourceId = LocalAppPreferencesState.current.animeSource
    val listState = rememberSaveable(selectedSourceId, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isSortVisible by remember { mutableStateOf(true) }
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val availableSorts = remember(legacyFilterCatalog?.capabilities) {
        legacyFilterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }
    val hasCatalogFilters = legacyFilterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true

    LaunchedEffect(hasCatalogFilters) {
        if (!hasCatalogFilters) isFilterSheetOpen = false
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
    AppCatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortVisible = it },
    )

    Box(modifier = modifier.fillMaxSize()) {
        org.akkirrai.hibiki.shared.design.component.AppContentState(
            isLoading = state.isLoading,
            hasContent = state.items.isNotEmpty(),
            errorMessage = state.error,
            errorTitle = stringResource(R.string.catalog_error_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::load,
            errorIcon = Icons.Outlined.WarningAmber,
            errorIconTint = MaterialTheme.colorScheme.error,
            content = {
                AppCatalogContentList(
                    state = listState,
                    topContentPadding = CATALOG_CONTENT_TOP_PADDING,
                    bottomContentPadding = bottomContentPadding,
                    content = {
                    appVerticalAnimeListContent(
                        items = state.items,
                        metaText = { anime -> anime.buildCardMeta(
                                announcementLabel = announcementLabel,
                                movieLabel = movieLabel,
                                maxSubtitleParts = 2,
                                separator = " • ",
                        ) },
                        onAnimeClick = onAnimeClick,
                        posterContent = { anime ->
                            PosterImage(
                                primaryUrl = anime.posterUrl,
                                fallbackUrl = anime.posterFallbackUrl,
                                contentDescription = anime.title,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    AppPosterPlaceholder(
                                        icon = Icons.Outlined.Image,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f),
                                    )
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
                            AppLoadMoreState(
                                isLoading = true,
                                errorMessage = null,
                                errorIcon = Icons.Outlined.WarningAmber,
                                onRetry = viewModel::loadMore,
                            )
                        }
                    }

                    if (state.isLoadingMore && state.error != null) {
                        item(key = "catalog_load_more_error") {
                            AppLoadMoreState(
                                isLoading = false,
                                errorMessage = state.error,
                                errorIcon = Icons.Outlined.WarningAmber,
                                onRetry = viewModel::loadMore,
                            )
                        }
                    }
                    },
                )
            },
        )

        // Keep the current results visible while a filter request is running, but make the
        // refresh state explicit instead of leaving the catalog looking unresponsive.
        if (state.isLoading && !state.isLoadingMore && state.items.isNotEmpty()) {
            AppCenteredLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
            )
        }

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

        AppCatalogTopOverlay(
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            onClear = { viewModel.updateQuery("") },
            placeholder = stringResource(R.string.search_placeholder),
            filterContentDescription = stringResource(R.string.search_filters),
            clearContentDescription = stringResource(R.string.home_search_clear),
            onFilterClick = { isFilterSheetOpen = true },
            showFilterButton = hasCatalogFilters,
            headerTopPadding = CATALOG_HEADER_TOP_PADDING,
            sortVerticalGap = CATALOG_SORT_VERTICAL_GAP,
            sortModifier = Modifier.graphicsLayer {
                translationY = sortOffsetY.toPx()
                alpha = sortAlpha
            },
            sortContent = {
                CatalogSortControl(
                    selectedSort = selectedSort,
                    availableSorts = availableSorts,
                    expanded = isSortMenuOpen,
                    onExpandedChange = { isSortMenuOpen = it },
                    onSortSelected = viewModel::selectSort,
                )
            },
            searchIcon = Icons.Outlined.Search,
            filterIcon = Icons.Outlined.FilterList,
            clearIcon = Icons.Outlined.Close,
            modifier = Modifier.align(Alignment.TopStart),
        )
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
        AppCatalogSortPill(
            sortKey = selectedSort.name,
            icon = selectedSort.icon,
            label = sortLabels[selectedSort].orEmpty(),
            onClick = { onExpandedChange(!expanded) },
            orderContent = {
                CatalogSortOrderIcon(
                    atEnd = expanded,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            },
            modifier = Modifier.align(Alignment.Center),
        )

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
                AppCatalogSortMenuItem(
                    icon = sort.icon,
                    label = sortLabels.getValue(sort),
                    selected = sort == selectedSort,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onSortSelected(sort)
                    },
                    orderContent = { orderModifier ->
                        CatalogSortOrderIcon(
                            atEnd = expanded,
                            modifier = orderModifier,
                        )
                    },
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
    initialSourceId: SourceId,
) : ViewModel() {
    private val presenter = AnimeCatalogPresenter(
        repository = repository,
        scope = viewModelScope,
        pageSize = 50,
    )
    val uiState: StateFlow<AnimeCatalogUiState> = presenter.state
    private val descriptionRequests = ConcurrentHashMap.newKeySet<String>()
    private var sourceGeneration = 0L
    private val sourceSessions = ConcurrentHashMap<String, AnimeCatalogUiState>()
    private var activeSourceId = initialSourceId

    init {
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect { sourceId ->
                sourceSessions[activeSourceId.value] = presenter.state.value
                sourceGeneration += 1
                activeSourceId = sourceId
                descriptionRequests.clear()
                sourceSessions[sourceId.value]?.let { cached ->
                    presenter.restore(cached)
                    if (cached.filterCatalog == null) presenter.loadFilterCatalog()
                } ?: run {
                    presenter.clear()
                    presenter.setFilters(AnimeSearchFilters())
                    presenter.setQuery("")
                    presenter.loadFilterCatalog()
                    presenter.search()
                }
            }
        }
        presenter.loadFilterCatalog()
    }

    fun load() = presenter.search()

    fun updateQuery(query: String) = presenter.setQuery(query)

    fun selectSort(sort: CatalogSort) {
        if (catalogSortFromAlias(uiState.value.filters.sortAlias) == sort) return
        presenter.setFilters(presenter.state.value.filters.copy(sortAlias = sort.toAlias()))
        load()
    }

    fun enrichDescription(anime: Anime) {
        if (!anime.description.isNullOrBlank() || !descriptionRequests.add(anime.id)) return
        val generation = sourceGeneration
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.enrichDescription(anime) }
                .onSuccess { enriched ->
                    if (generation == sourceGeneration && !enriched.description.isNullOrBlank()) {
                        presenter.updateItem(enriched)
                    }
                }
                .also { descriptionRequests.remove(anime.id) }
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
                initialSourceId = AppPreferences.readState(context.applicationContext).animeSource,
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

private val CatalogSort.labelRes: Int
    get() = when (this) {
        CatalogSort.Alphabetical -> R.string.catalog_sort_alphabetical
        CatalogSort.Popular -> R.string.catalog_sort_popular
        CatalogSort.Updated -> R.string.catalog_sort_updated
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
    CATALOG_SORT_VERTICAL_GAP +
    CATALOG_SORT_CONTROL_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP
private const val CATALOG_SORT_ANIMATION_DURATION_MS = 220
private const val CATALOG_SCROLL_THRESHOLD = 3
