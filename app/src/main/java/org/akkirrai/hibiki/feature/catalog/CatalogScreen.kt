package org.akkirrai.hibiki.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AnimePosterCardItem
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFloatingPill
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.app.settings.withAppPreferencesLanguage

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
    val gridState = rememberLazyGridState()
    var isCategorySheetOpen by remember { mutableStateOf(false) }
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val baseContext = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedContext = remember(baseContext, appLanguage) {
        baseContext.withLanguage(appLanguage)
    }
    val catalogTitle = localizedContext.getString(R.string.nav_catalog)
    val categoriesTitle = localizedContext.getString(R.string.catalog_categories_title)
    val categoriesSubtitle = localizedContext.getString(R.string.catalog_categories_subtitle)
    val selectedCategoriesLabel = when (state.selectedCategories.size) {
        0 -> categoriesTitle
        1 -> state.selectedCategories.first().title
        else -> "$categoriesTitle (${state.selectedCategories.size})"
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
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
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 118.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = 86.dp,
                        end = UiDimens.ScreenPadding,
                        bottom = bottomContentPadding + UiDimens.ScreenPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (state.description != null) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "catalog_description",
                        ) {
                            Text(
                                text = state.description.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    items(
                        items = state.items,
                        key = { it.anime.id },
                    ) { item ->
                        AnimePosterCardItem(
                            anime = item.anime,
                            metaText = item.anime.buildCardMeta(
                                announcementLabel = announcementLabel,
                                maxSubtitleParts = 2,
                                separator = " • ",
                            ),
                            onClick = { onAnimeClick(item.anime) },
                            modifier = Modifier.fillMaxWidth(),
                            titleBaseMaxLines = 3,
                            titleExtraLongTitleLines = 1,
                            titleOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }

                    if (state.isLoadingMore) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "catalog_loading_more",
                        ) {
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
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "catalog_load_more_error",
                        ) {
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

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 14.dp, start = UiDimens.ScreenPadding, end = UiDimens.ScreenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppFloatingPill {
                Text(
                    text = catalogTitle,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            AppFloatingPill(
                modifier = Modifier.clickable { isCategorySheetOpen = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = selectedCategoriesLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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

    init {
        load()
    }

    fun load() {
        val selectedCategories = _uiState.value.selectedCategories
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
                )
            }.onSuccess { page ->
                _uiState.value = CatalogUiState(
                    isLoading = false,
                    title = "",
                    description = page.description,
                    categories = page.categories,
                    selectedCategories = selectedCategories,
                    items = page.items,
                    currentPage = page.currentPage,
                    canLoadMore = page.canLoadMore,
                )
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
    val items: List<CatalogAnimeCard> = emptyList(),
    val currentPage: Int = 0,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
)

private const val CATALOG_SCROLL_THRESHOLD = 3
