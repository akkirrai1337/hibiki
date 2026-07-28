package org.akkirrai.hibiki.feature.catalog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.shared.library.icon
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.feature.home.AnimeSearchFiltersSheet
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.CatalogSort
import org.akkirrai.hibiki.shared.catalog.CatalogSortVerticalGap
import org.akkirrai.hibiki.shared.catalog.CatalogSortControlHeight
import org.akkirrai.hibiki.shared.catalog.CatalogContentTopPadding
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuWidth
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuOffsetY
import org.akkirrai.hibiki.shared.catalog.CatalogSortMenuCornerRadius
import org.akkirrai.hibiki.shared.catalog.CatalogSortAnimationDurationMs
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortControl
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortOrderIcon
import org.akkirrai.hibiki.shared.catalog.AppCatalogContentList
import org.akkirrai.hibiki.shared.catalog.AppCatalogTopOverlay
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortMenuContent
import org.akkirrai.hibiki.shared.catalog.AppCatalogSortVisibilityEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogPaginationEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogQueryEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogFilterVisibilityEffect
import org.akkirrai.hibiki.shared.catalog.AppCatalogRefreshingState
import org.akkirrai.hibiki.shared.catalog.AppCatalogContentState
import org.akkirrai.hibiki.shared.catalog.appCatalogResultsContent
import org.akkirrai.hibiki.shared.catalog.catalogSortFromAlias
import org.akkirrai.hibiki.shared.catalog.fallbackCatalogSort
import org.akkirrai.hibiki.shared.catalog.icon
import org.akkirrai.hibiki.shared.catalog.toAlias
import org.akkirrai.hibiki.shared.catalog.availableCatalogSorts
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter
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
    val availableSorts = remember(state.filterCatalog?.capabilities) {
        state.filterCatalog?.capabilities?.let(::availableCatalogSorts) ?: CatalogSort.entries
    }
    val hasCatalogFilters = state.filterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true

    AppCatalogFilterVisibilityEffect(
        hasFilters = hasCatalogFilters,
        onFiltersUnavailable = { isFilterSheetOpen = false },
    )

    LaunchedEffect(availableSorts, selectedSort) {
        if (selectedSort !in availableSorts) {
            val capabilities = state.filterCatalog?.capabilities
            fallbackCatalogSort(
                supportedSortAlias = capabilities?.supportedSorts?.firstOrNull(),
                availableSorts = availableSorts,
            )?.let(viewModel::selectSort)
        }
    }

    AppCatalogQueryEffect(
        query = state.query,
        onQuerySettled = viewModel::load,
    )
    AppCatalogPaginationEffect(
        listState = listState,
        state = state,
        onLoadMore = viewModel::loadMore,
    )
    AppCatalogSortVisibilityEffect(
        listState = listState,
        onVisibilityChange = { isSortVisible = it },
    )

    Box(modifier = modifier.fillMaxSize()) {
        AppCatalogContentState(
            isLoading = state.isLoading,
            hasContent = state.items.isNotEmpty(),
            errorMessage = state.error,
            errorTitle = stringResource(R.string.catalog_error_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::load,
            content = {
                AppCatalogContentList(
                    state = listState,
                    topContentPadding = CatalogContentTopPadding,
                    bottomContentPadding = bottomContentPadding,
                    content = {
                    appCatalogResultsContent(
                        items = state.items,
                        announcementLabel = announcementLabel,
                        movieLabel = movieLabel,
                        onAnimeClick = onAnimeClick,
                        posterContent = { anime ->
                            AppPosterImage(
                                primaryUrl = anime.posterUrl,
                                fallbackUrl = anime.posterFallbackUrl,
                                contentDescription = anime.title,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    AppPosterPlaceholder(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f),
                                    )
                                },
                            )
                        },
                        posterFooterContent = { anime ->
                            libraryStatusByAnimeId[anime.id]?.let { category ->
                                LibraryStatusPosterFooter(
                                    label = stringResource(category.labelResId),
                                    icon = category.icon(),
                                )
                            }
                        },
                        onItemVisible = viewModel::enrichDescription,
                        isLoadingMore = state.isLoadingMore,
                        paginationErrorMessage = state.error,
                        onLoadMoreRetry = viewModel::loadMore,
                    )
                    },
                )
            },
        )

        // Keep the current results visible while a filter request is running, but make the
        // refresh state explicit instead of leaving the catalog looking unresponsive.
        AppCatalogRefreshingState(
            isRefreshing = state.isLoading && !state.isLoadingMore && state.items.isNotEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        )

        val sortOffsetY by animateDpAsState(
            targetValue = if (isSortVisible) {
                0.dp
            } else {
                -(CatalogSortControlHeight + CatalogSortVerticalGap)
            },
            animationSpec = tween(durationMillis = CatalogSortAnimationDurationMs),
            label = "catalog_sort_offset",
        )
        val sortAlpha by animateFloatAsState(
            targetValue = if (isSortVisible) 1f else 0f,
            animationSpec = tween(durationMillis = CatalogSortAnimationDurationMs),
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
            modifier = Modifier.align(Alignment.TopStart),
        )
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

    AppCatalogSortControl(
        sortKey = selectedSort.name,
        icon = selectedSort.icon(),
        label = sortLabels[selectedSort].orEmpty(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        orderContent = { orderModifier ->
            AppCatalogSortOrderIcon(
                atEnd = expanded,
                modifier = orderModifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        },
        menuContent = {
            val layoutDirection = LocalLayoutDirection.current
            val screenWidth = LocalWindowInfo.current.containerSize.width
            val density = androidx.compose.ui.platform.LocalDensity.current
            val screenWidthDp = with(density) { screenWidth.toDp() }
            val horizontalInsets = UiDimens.ScreenPadding * 2
            val menuWidth = CatalogSortMenuWidth
            val offsetX = (screenWidthDp - horizontalInsets - menuWidth) / 2

            CascadeDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                state = cascadeState,
                offset = DpOffset(
                    x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) offsetX else -offsetX,
                    y = CatalogSortMenuOffsetY,
                ),
                shape = RoundedCornerShape(CatalogSortMenuCornerRadius),
            ) {
                AppCatalogSortMenuContent(
                title = sortTitle,
                sorts = availableSorts,
                selectedSort = selectedSort,
                label = sortLabels::getValue,
                expanded = expanded,
                onSortSelected = { sort ->
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onSortSelected(sort)
                },
                orderContent = { atEnd, orderModifier ->
                    AppCatalogSortOrderIcon(
                        atEnd = atEnd,
                        modifier = orderModifier,
                    )
                },
                )
            }
        },
        modifier = modifier,
    )
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

private val CatalogSort.labelRes: Int
    get() = when (this) {
        CatalogSort.Alphabetical -> R.string.catalog_sort_alphabetical
        CatalogSort.Popular -> R.string.catalog_sort_popular
        CatalogSort.Updated -> R.string.catalog_sort_updated
    }
