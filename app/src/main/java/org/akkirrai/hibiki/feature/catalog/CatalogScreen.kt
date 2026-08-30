package org.akkirrai.hibiki.feature.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.network.NoInternetConnectionException
import org.akkirrai.hibiki.core.network.hasActiveInternetConnection
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.anime.verticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedCardModifier
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedPosterModifier
import org.akkirrai.hibiki.core.design.component.anime.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.anime.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogFeature
import kotlinx.coroutines.delay
import me.saket.cascade.CascadeDropdownMenu
import me.saket.cascade.rememberCascadeState

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CatalogScreen(
    onAnimeClick: (Anime) -> Unit,
    onOpenSources: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModel.Factory(LocalContext.current),
    ),
) {
    val uiState = viewModel.uiState.collectAsState()
    val state = uiState.value
    val noSourcesInstalled = AnimeSourceRegistry.sources.isEmpty()
    // Groups the fields the anime list actually renders behind one structurally-compared
    // snapshot, so pagination/list-affecting changes don't force LazyColumn to recompose
    // when unrelated fields (query, filters, selectedSort, filterCatalog) change instead.
    val listUiState by remember {
        derivedStateOf {
            val current = uiState.value
            CatalogAnimeListUiState(
                items = current.items.map { it.anime },
                description = current.description,
                isLoadingMore = current.isLoadingMore,
                loadMoreError = current.loadMoreError,
            )
        }
    }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val sharedCardModifier: @Composable (Anime) -> Modifier = { anime ->
        animeDetailsSharedCardModifier(anime.id, sharedTransitionScope, animatedVisibilityScope)
    }
    val sharedPosterModifier: @Composable (Anime) -> Modifier = { anime ->
        animeDetailsSharedPosterModifier(anime.id, sharedTransitionScope, animatedVisibilityScope)
    }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isSortScrollVisible by remember { mutableStateOf(true) }
    // One-way latch: stays true once the first load finishes, so later reloads (sort/filter
    // changes, which also flip isLoading) don't hide-then-reveal the sort control again --
    // only its very first appearance should slide out from under the search bar.
    var hasLoadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) hasLoadedOnce = true
    }
    val isSortVisible = hasLoadedOnce && isSortScrollVisible
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val availableSorts = remember(state.filterCatalog?.capabilities) {
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }
    val showSortControl = availableSorts.size > 1
    val catalogContentTopPadding = if (showSortControl) {
        CATALOG_CONTENT_TOP_PADDING
    } else {
        CATALOG_CONTENT_TOP_PADDING_WITHOUT_SORT
    }
    // Default to showing the filter control while capabilities are still loading (unknown), so
    // it doesn't visibly pop in a moment after the screen appears -- only hide it once we
    // actually know the source has nothing to filter by.
    val showFilterControl = state.filterCatalog?.capabilities?.supportedFilters?.isNotEmpty() ?: true

    LaunchedEffect(availableSorts, state.selectedSort) {
        if (state.selectedSort !in availableSorts) {
            val capabilities = state.filterCatalog?.capabilities
            val fallback = availableSorts.firstOrNull { it.searchSort == capabilities?.fallbackSort }
                ?: availableSorts.firstOrNull()
            fallback?.let(viewModel::selectSort)
        }
    }

    CatalogPaginationEffect(
        listState = listState,
        state = state,
        onLoadMore = viewModel::loadMore,
    )
    CatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortScrollVisible = it },
    )
    CatalogDescriptionPrefetchEffect(
        listState = listState,
        items = listUiState.items,
        onPrefetch = viewModel::enrichDescription,
    )

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                AppCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                AppMessageState(
                    title = stringResource(R.string.catalog_error_title),
                    message = state.errorMessage.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(UiDimens.ScreenPadding),
                    actionLabel = stringResource(
                        if (noSourcesInstalled) R.string.action_open_sources else R.string.search_retry,
                    ),
                    onActionClick = if (noSourcesInstalled) onOpenSources else viewModel::load,
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = viewModel::refresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isLoading,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                    top = if (showSortControl) {
                                        CATALOG_PULL_REFRESH_INDICATOR_TOP_OFFSET
                                    } else {
                                        CATALOG_PULL_REFRESH_INDICATOR_TOP_OFFSET_WITHOUT_SORT
                                    },
                                ),
                        )
                    },
                ) {
                    CatalogAnimeListContent(
                        listState = listState,
                        listUiState = listUiState,
                        contentTopPadding = catalogContentTopPadding,
                        bottomContentPadding = bottomContentPadding,
                        announcementLabel = announcementLabel,
                        movieLabel = movieLabel,
                        onAnimeClick = onAnimeClick,
                        libraryStatusByAnimeId = libraryStatusByAnimeId,
                        onRetryLoadMore = viewModel::loadMore,
                    onItemVisible = viewModel::enrichDescription,
                    sharedCardModifier = sharedCardModifier,
                    sharedPosterModifier = sharedPosterModifier,
                )
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
                showFilter = showFilterControl,
                modifier = Modifier.zIndex(1f),
            )
            if (showSortControl) {
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
                    selectedSort = state.selectedSort,
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
    }

    if (isFilterSheetOpen) {
        AnimeSearchFiltersSheet(
            initialFilters = state.filters,
            filterCatalog = state.filterCatalog,
            isFilterCatalogLoading = state.isLoading && state.filterCatalog == null,
            onApply = viewModel::applyFilters,
            onDismissRequest = { isFilterSheetOpen = false },
        )
    }
}

/** The subset of [CatalogUiState] the anime list renders, compared structurally so unrelated
 * state changes (query, filters, selectedSort, filterCatalog) don't force it to recompose. */
private data class CatalogAnimeListUiState(
    val items: List<Anime>,
    val description: String?,
    val isLoadingMore: Boolean,
    val loadMoreError: String?,
)

@Composable
private fun CatalogAnimeListContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    listUiState: CatalogAnimeListUiState,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    announcementLabel: String,
    movieLabel: String,
    onAnimeClick: (Anime) -> Unit,
    libraryStatusByAnimeId: Map<String, org.akkirrai.hibiki.core.source.LibraryCategory>,
    onRetryLoadMore: () -> Unit,
    onItemVisible: (Anime) -> Unit,
    sharedCardModifier: @Composable (Anime) -> Modifier,
    sharedPosterModifier: @Composable (Anime) -> Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = contentTopPadding,
            end = UiDimens.ScreenPadding,
            bottom = bottomContentPadding + UiDimens.ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (listUiState.description != null) {
            item(key = "catalog_description") {
                Text(
                    text = listUiState.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        verticalAnimeListContent(
            items = listUiState.items,
            metaText = { anime -> anime.buildCardMeta(
                    announcementLabel = announcementLabel,
                    movieLabel = movieLabel,
                    maxSubtitleParts = 2,
                    separator = " • ",
            ) },
            onAnimeClick = onAnimeClick,
            posterFooterContent = { anime ->
                libraryStatusByAnimeId[anime.id]?.let { category ->
                    LibraryStatusPosterFooter(category)
                }
            },
            onItemVisible = onItemVisible,
            sharedCardModifier = sharedCardModifier,
            sharedPosterModifier = sharedPosterModifier,
        )

        if (listUiState.isLoadingMore) {
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

        if (listUiState.loadMoreError != null) {
            item(key = "catalog_load_more_error") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRetryLoadMore)
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
                        text = listUiState.loadMoreError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogPaginationEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    state: CatalogUiState,
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
                latestState.loadMoreError == null
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) onLoadMore()
        }
    }
}

/** Starts fetching descriptions for cards a few rows below the visible window, not just the
 * ones already on screen - so by the time the user actually scrolls to them, the description is
 * often already there instead of popping in a beat late. Safe to call redundantly: `enrichDescription`
 * already dedupes in-flight/complete requests, so a card that's already visible or already
 * enriched is a no-op here. */
@Composable
private fun CatalogDescriptionPrefetchEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    items: List<Anime>,
    onPrefetch: (Anime) -> Unit,
) {
    val latestItems by rememberUpdatedState(items)
    val latestOnPrefetch by rememberUpdatedState(onPrefetch)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex == null) return@collect
                for (index in (lastVisibleIndex + 1)..(lastVisibleIndex + CATALOG_DESCRIPTION_READ_AHEAD)) {
                    latestItems.getOrNull(index)?.let(latestOnPrefetch)
                }
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
    private val errorContext: android.content.Context,
) : ViewModel() {
    private var activeSource = AppPreferences.readState(errorContext).animeSource
    private val _uiState = MutableStateFlow(
        CatalogUiState(
            isLoading = true,
            selectedSort = catalogSortFor(activeSource),
        )
    )
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val descriptionUpdates = Channel<Pair<String, String>>(Channel.UNLIMITED)
    private val descriptionRequests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        load()
        viewModelScope.launch {
            AppPreferences.animeSourceChanges.collect { source ->
                AppPreferences.saveCatalogSort(
                    context = errorContext,
                    source = activeSource,
                    sort = _uiState.value.selectedSort.name,
                )
                activeSource = source
                _uiState.update { state ->
                    state.copy(
                        filterCatalog = null,
                        filters = AnimeSearchFilters(),
                        selectedSort = catalogSortFor(source),
                        items = emptyList(),
                        currentPage = 0,
                        canLoadMore = false,
                    )
                }
                load()
            }
        }
        observeDescriptionUpdates()
    }

    /** Some sources (e.g. AnimePahe) don't include a description on catalog/search cards at
     * all - only on the details page. Fetches it lazily as cards scroll into view (wired to
     * each card's onItemVisible) instead of blocking the whole page load. Only the
     * `description` field is ever merged back into the card - the details fetch can return a
     * less complete `Anime` than the catalog parse did (e.g. a source's details page missing a
     * field the listing had), and swapping in the whole object used to silently drop those
     * fields (year disappearing from the card's meta line was one instance). */
    fun enrichDescription(anime: Anime) {
        if (!anime.description.isNullOrBlank() || !descriptionRequests.add(anime.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.enrichDescription(anime) }
                .onSuccess { enriched ->
                    val description = enriched.description
                    if (!description.isNullOrBlank()) descriptionUpdates.trySend(anime.id to description)
                }
                .also { descriptionRequests.remove(anime.id) }
        }
    }

    private fun observeDescriptionUpdates() {
        viewModelScope.launch {
            for (firstUpdate in descriptionUpdates) {
                val updates = linkedMapOf(firstUpdate)
                delay(DESCRIPTION_UPDATE_BATCH_WINDOW_MS)
                while (true) {
                    val nextUpdate = descriptionUpdates.tryReceive().getOrNull() ?: break
                    updates[nextUpdate.first] = nextUpdate.second
                }
                _uiState.update { state -> state.replaceDescriptions(updates) }
            }
        }
    }

    private fun CatalogUiState.replaceDescriptions(updates: Map<String, String>): CatalogUiState {
        var changed = false
        val updatedItems = items.map { card ->
            updates[card.anime.id]?.let { description ->
                changed = true
                CatalogAnimeCard(card.anime.copy(description = description))
            } ?: card
        }
        return if (changed) copy(items = updatedItems) else this
    }

    fun load() {
        val currentState = _uiState.value
        val filters = currentState.filters
        val query = currentState.query
        val sort = currentState.selectedSort
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    loadMoreError = null,
                )
            }
            runCatching {
                ensureInternetConnection()
                repository.loadPage(
                    page = 1,
                    filters = filters,
                    query = query,
                    sort = sort,
                )
            }.onSuccess { page ->
                val knownDescriptions = currentState.items
                    .mapNotNull { card -> card.anime.description?.takeIf { it.isNotBlank() }?.let { card.anime.id to it } }
                    .toMap()
                val withKnownDescriptions = page.items.map { card ->
                    if (!card.anime.description.isNullOrBlank()) return@map card
                    val known = knownDescriptions[card.anime.id] ?: return@map card
                    CatalogAnimeCard(card.anime.copy(description = known))
                }
                val items = eagerlyEnrichFirstItems(withKnownDescriptions)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        title = "",
                        description = page.description,
                        filterCatalog = page.filterCatalog,
                        items = items,
                        currentPage = page.currentPage,
                        canLoadMore = page.canLoadMore,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: errorContext.getString(R.string.catalog_error_title),
                    )
                }
            }
        }
    }

    /** The first frame is rendered only after its above-the-fold cards have a description, so
     * they don't visibly pop in moments after the catalog appears (mirrors Home's eager
     * treatment of its trending list). Only the first [EAGER_DESCRIPTION_COUNT] cards are
     * awaited here - the rest still fill in lazily as they scroll into view via
     * [enrichDescription]. A source that already includes descriptions in its catalog listing
     * (e.g. AnimeVost) pays nothing extra here: every eager item's description is already
     * non-blank, so the network call is skipped entirely. */
    private suspend fun eagerlyEnrichFirstItems(items: List<CatalogAnimeCard>): List<CatalogAnimeCard> {
        val eagerCount = EAGER_DESCRIPTION_COUNT.coerceAtMost(items.size)
        if (eagerCount == 0) return items
        val enrichedEager = coroutineScope {
            items.take(eagerCount).map { card ->
                async {
                    if (!card.anime.description.isNullOrBlank()) return@async card
                    val description = runCatching { repository.enrichDescription(card.anime) }
                        .getOrNull()?.description
                    if (description.isNullOrBlank()) card else CatalogAnimeCard(card.anime.copy(description = description))
                }
            }.awaitAll()
        }
        return enrichedEager + items.drop(eagerCount)
    }

    fun updateQuery(query: String) {
        if (query == _uiState.value.query) return
        _uiState.update { it.copy(query = query, items = emptyList(), currentPage = 0, canLoadMore = false) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun selectSort(sort: CatalogSort) {
        if (_uiState.value.selectedSort == sort) return
        AppPreferences.saveCatalogSort(
            context = errorContext,
            source = activeSource,
            sort = sort.name,
        )
        _uiState.update { it.copy(selectedSort = sort, items = emptyList(), currentPage = 0, canLoadMore = false) }
        load()
    }

    fun refresh() {
        if (!_uiState.value.isLoading) load()
    }

    private fun catalogSortFor(source: org.akkirrai.beakokit.api.SourceId): CatalogSort {
        return AppPreferences.readCatalogSort(errorContext, source)
            ?.let { stored -> runCatching { CatalogSort.valueOf(stored) }.getOrNull() }
            ?: CatalogSort.Popular
    }

    fun applyFilters(filters: AnimeSearchFilters) {
        _uiState.update {
            it.copy(
                filters = filters,
                items = emptyList(),
                currentPage = 0,
                canLoadMore = false,
            )
        }
        load()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            val nextPage = state.currentPage + 1
            _uiState.update {
                it.copy(
                    isLoadingMore = true,
                    loadMoreError = null,
                )
            }
            runCatching {
                ensureInternetConnection()
                repository.loadPage(
                    page = nextPage,
                    filters = state.filters,
                    query = state.query,
                    sort = state.selectedSort,
                )
            }.onSuccess { page ->
                _uiState.update { current ->
                    val merged = (current.items + page.items).distinctBy { it.anime.id }
                    current.copy(
                        isLoadingMore = false,
                        title = current.title,
                        description = page.description ?: current.description,
                        filterCatalog = current.filterCatalog ?: page.filterCatalog,
                        items = merged,
                        currentPage = page.currentPage,
                        canLoadMore = page.canLoadMore,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = throwable.message ?: errorContext.getString(R.string.catalog_load_more_error),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        descriptionUpdates.close()
        repository.close()
        super.onCleared()
    }

    private fun ensureInternetConnection() {
        if (!hasActiveInternetConnection(errorContext)) {
            throw NoInternetConnectionException(errorContext.getString(R.string.home_error_no_internet))
        }
    }

    private companion object {
        const val DESCRIPTION_UPDATE_BATCH_WINDOW_MS = 100L
        const val EAGER_DESCRIPTION_COUNT = 6
    }

    class Factory(
        private val context: android.content.Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val localizedContext = context.applicationContext.withAppPreferencesLanguage()
            return CatalogViewModel(
                repository = CatalogRepository(context.applicationContext),
                errorContext = localizedContext,
            ) as T
        }
    }
}

data class CatalogUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val description: String? = null,
    val filterCatalog: org.akkirrai.beakokit.model.AnimeSearchFilterCatalog? = null,
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
    val query: String = "",
    val selectedSort: CatalogSort = CatalogSort.Popular,
    val items: List<CatalogAnimeCard> = emptyList(),
    val currentPage: Int = 0,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
)

enum class CatalogSort(@androidx.annotation.StringRes val labelRes: Int) {
    Alphabetical(R.string.catalog_sort_alphabetical),
    Popular(R.string.catalog_sort_popular),
    Updated(R.string.catalog_sort_updated),
}

internal val CatalogSort.searchSort: AnimeSearchSort
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
private val CATALOG_CONTENT_TOP_PADDING_WITHOUT_SORT = CATALOG_HEADER_TOP_PADDING + CATALOG_SEARCH_BAR_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP
private val CATALOG_PULL_REFRESH_INDICATOR_TOP_OFFSET = CATALOG_HEADER_TOP_PADDING +
    CATALOG_SEARCH_BAR_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP +
    CATALOG_SORT_CONTROL_HEIGHT - 8.dp
private val CATALOG_PULL_REFRESH_INDICATOR_TOP_OFFSET_WITHOUT_SORT =
    CATALOG_HEADER_TOP_PADDING + CATALOG_SEARCH_BAR_HEIGHT - 8.dp
private const val CATALOG_SORT_ANIMATION_DURATION_MS = 220
private const val CATALOG_SCROLL_THRESHOLD = 3
private const val CATALOG_DESCRIPTION_READ_AHEAD = 3
