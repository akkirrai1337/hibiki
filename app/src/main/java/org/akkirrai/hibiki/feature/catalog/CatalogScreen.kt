package org.akkirrai.hibiki.feature.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.verticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage
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
    val listState = rememberLazyListState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    var isCategorySheetOpen by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val categoriesTitle = stringResource(R.string.catalog_categories_title)
    val categoriesSubtitle = stringResource(R.string.catalog_categories_subtitle)

    LaunchedEffect(state.query) {
        delay(350)
        viewModel.load()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearEnd = lastVisibleItem >= totalItems - CATALOG_SCROLL_THRESHOLD
            isNearEnd && !state.isLoading && !state.isLoadingMore && state.canLoadMore && state.loadMoreError == null
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.loadMore()
            }
        }
    }

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
                    if (state.description != null) {
                        item(key = "catalog_description") {
                            Text(
                                text = state.description.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    verticalAnimeListContent(
                        items = state.items.map { it.anime },
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

                    if (state.loadMoreError != null) {
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
                                    text = state.loadMoreError.orEmpty(),
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
                onFilterClick = { isCategorySheetOpen = true },
            )
            CatalogSortControl(
                selectedSort = state.selectedSort,
                expanded = isSortMenuOpen,
                onExpandedChange = { isSortMenuOpen = it },
                onSortSelected = viewModel::selectSort,
            )
        }
    }

    if (isCategorySheetOpen) {
        CatalogCategorySheet(
            categories = state.categories,
            title = categoriesTitle,
            subtitle = categoriesSubtitle,
            selectedCategories = state.selectedCategories,
            onCategoryClick = viewModel::toggleCategory,
            onDismiss = { isCategorySheetOpen = false },
        )
    }
}

@Composable
private fun CatalogSortControl(
    selectedSort: CatalogSort,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
) {
    val cascadeState = rememberCascadeState()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
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
                    text = stringResource(sort.labelRes),
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
                text = stringResource(R.string.catalog_sort_title),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally),
            )
            CatalogSort.entries.forEach { sort ->
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
                                Text(stringResource(sort.labelRes))
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CatalogCategorySheet(
    categories: List<CatalogCategory>,
    title: String,
    subtitle: String,
    selectedCategories: List<CatalogCategory>,
    onCategoryClick: (CatalogCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.padding(
                start = UiDimens.ScreenPadding,
                    top = 4.dp,
                end = UiDimens.ScreenPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = UiDimens.ScreenPadding,
                        end = UiDimens.ScreenPadding,
                        bottom = UiDimens.SectionSpacing,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategories.any { it.genreAlias == category.genreAlias }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryClick(category) },
                        label = { Text(text = category.title) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val errorContext: android.content.Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState(isLoading = true))
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    fun load() {
        val currentState = _uiState.value
        val selectedCategories = currentState.selectedCategories
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
                repository.loadPage(
                    page = 1,
                    categories = selectedCategories,
                    query = query,
                    sort = sort,
                )
            }.onSuccess { page ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        title = "",
                        description = page.description,
                        categories = page.categories,
                        items = page.items,
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

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, items = emptyList(), currentPage = 0, canLoadMore = false) }
    }

    fun selectSort(sort: CatalogSort) {
        if (_uiState.value.selectedSort == sort) return
        _uiState.update { it.copy(selectedSort = sort, items = emptyList(), currentPage = 0, canLoadMore = false) }
        load()
    }

    fun toggleCategory(category: CatalogCategory) {
        _uiState.update {
            val isSelected = it.selectedCategories.any { selected -> selected.genreAlias == category.genreAlias }
            it.copy(
                selectedCategories = if (isSelected) {
                    it.selectedCategories.filterNot { selected -> selected.genreAlias == category.genreAlias }
                } else {
                    it.selectedCategories + category
                },
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
                repository.loadPage(
                    page = nextPage,
                    categories = state.selectedCategories,
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
                        categories = if (current.categories.isEmpty()) page.categories else current.categories,
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
        repository.close()
        super.onCleared()
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
    val categories: List<CatalogCategory> = emptyList(),
    val selectedCategories: List<CatalogCategory> = emptyList(),
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

private val CATALOG_HEADER_TOP_PADDING = UiDimens.SearchBarTopPadding
private val CATALOG_SEARCH_BAR_HEIGHT = UiDimens.SearchBarHeight
private val CATALOG_SORT_VERTICAL_GAP = 8.dp
private val CATALOG_SORT_CONTROL_HEIGHT = 28.dp
private val CATALOG_CONTENT_TOP_PADDING = CATALOG_HEADER_TOP_PADDING +
    CATALOG_SEARCH_BAR_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP +
    CATALOG_SORT_CONTROL_HEIGHT +
    CATALOG_SORT_VERTICAL_GAP
private const val CATALOG_SCROLL_THRESHOLD = 3
