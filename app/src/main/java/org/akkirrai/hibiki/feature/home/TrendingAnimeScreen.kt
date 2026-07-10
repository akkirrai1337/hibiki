package org.akkirrai.hibiki.feature.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.core.design.component.AppFloatingPill
import org.akkirrai.hibiki.core.design.component.AnimePosterCardItem
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildCardMeta

@Composable
fun TrendingAnimeScreen(
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingAnimeViewModel = viewModel(
        factory = TrendingAnimeViewModel.Factory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearEnd = lastVisibleItem >= totalItems - TRENDING_SCROLL_THRESHOLD
            val canTrigger = !state.isLoading && !state.isLoadingMore && state.canLoadMore && state.loadMoreError == null
            isNearEnd && canTrigger
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.loadMore()
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                AppCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                TrendingErrorState(
                    title = stringResource(R.string.trending_error_title),
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::load,
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
                        bottom = UiDimens.ScreenPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.items, key = Anime::id) { anime ->
                        AnimePosterCardItem(
                            anime = anime,
                            metaText = buildTrendingMeta(anime),
                            onClick = { onAnimeClick(anime) },
                            width = 118.dp,
                        )
                    }

                    if (state.isLoadingMore) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "trending_loading_more",
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
                            key = "trending_load_more_error",
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadMore() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
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
        }

        AppFloatingHeader(
            title = stringResource(R.string.home_trending),
            onBackClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            actions = {
                TrendingFilterButton(
                    selectedFilter = state.selectedFilter,
                    onFilterClick = viewModel::selectFilter,
                )
            },
        )
    }
}

@Composable
private fun TrendingFilterButton(
    selectedFilter: TrendingFilter,
    onFilterClick: (TrendingFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AppFloatingPill(
            modifier = Modifier
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(selectedFilter.titleResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TrendingFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(filter.titleResId)) },
                    onClick = {
                        expanded = false
                        onFilterClick(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun TrendingErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = Modifier
            .fillMaxSize()
            .padding(UiDimens.ScreenPadding),
        actionLabel = stringResource(R.string.search_retry),
        onActionClick = onRetry,
        icon = Icons.Outlined.WarningAmber,
        iconTint = MaterialTheme.colorScheme.error,
    )
}

class TrendingAnimeViewModel(
    private val repository: HomeRepository,
    private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendingAnimeUiState(isLoading = true))
    val uiState: StateFlow<TrendingAnimeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectFilter(filter: TrendingFilter) {
        if (filter == _uiState.value.selectedFilter) return
        _uiState.value = TrendingAnimeUiState(
            isLoading = true,
            selectedFilter = filter,
        )
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedFilter = _uiState.value.selectedFilter
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    loadMoreError = null,
                )
            }
            runCatching {
                repository.loadTrendingPage(
                    offset = 0,
                    limit = TRENDING_PAGE_LIMIT,
                    filter = selectedFilter,
                )
            }
                .onSuccess { items ->
                    _uiState.value = TrendingAnimeUiState(
                        isLoading = false,
                        selectedFilter = selectedFilter,
                        items = items,
                        canLoadMore = items.size >= TRENDING_PAGE_LIMIT,
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: context.getString(R.string.trending_error_title),
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            val offset = _uiState.value.items.size
            val selectedFilter = _uiState.value.selectedFilter
            _uiState.update {
                it.copy(
                    isLoadingMore = true,
                    loadMoreError = null,
                )
            }
            runCatching {
                repository.loadTrendingPage(
                    offset = offset,
                    limit = TRENDING_PAGE_LIMIT,
                    filter = selectedFilter,
                )
            }
                .onSuccess { nextItems ->
                    _uiState.update { state ->
                        val mergedItems = (state.items + nextItems).distinctBy(Anime::id)
                        state.copy(
                            isLoadingMore = false,
                            items = mergedItems,
                            canLoadMore = nextItems.size >= TRENDING_PAGE_LIMIT,
                            loadMoreError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreError = throwable.message ?: context.getString(R.string.trending_load_more_error),
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
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dependencies = context.applicationContext.hibikiDependencies()
            return TrendingAnimeViewModel(
                repository = dependencies.homeRepository(),
                context = context.applicationContext,
            ) as T
        }
    }

    private companion object {
        const val TRENDING_PAGE_LIMIT = 100
    }
}

data class TrendingAnimeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilter: TrendingFilter = TrendingFilter.All,
    val items: List<Anime> = emptyList(),
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreError: String? = null,
)

@Composable
private fun buildTrendingMeta(anime: Anime): String {
    return anime.buildCardMeta(
        announcementLabel = stringResource(R.string.anime_meta_announcement),
        movieLabel = stringResource(R.string.anime_meta_movie),
        maxSubtitleParts = Int.MAX_VALUE,
        separator = " · ",
    )
}

private const val TRENDING_SCROLL_THRESHOLD = 3
