package org.akkirrai.hibiki.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.home.HomeDataRepository
import org.akkirrai.hibiki.shared.home.mergeAnimePreservingOrder
import org.akkirrai.hibiki.shared.home.TrendingAnimeUiState
import org.akkirrai.hibiki.shared.home.TrendingFilter
import org.akkirrai.hibiki.shared.home.TrendingPresenter
import org.akkirrai.hibiki.shared.design.component.AppFloatingHeader
import org.akkirrai.hibiki.shared.home.AppTrendingFilterButton
import org.akkirrai.hibiki.shared.home.AppTrendingScreenContent
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.buildCardMeta

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
    val listState = rememberLazyListState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
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
        AppTrendingScreenContent(
            state = state,
            listState = listState,
            errorTitle = stringResource(R.string.trending_error_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::load,
            onLoadMoreRetry = viewModel::loadMore,
            onAnimeClick = onAnimeClick,
            metaText = { anime -> buildTrendingMeta(anime) },
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            libraryStatusLabel = { category -> stringResource(category.labelResId) },
            modifier = Modifier.fillMaxSize(),
        )

        AppFloatingHeader(
            title = stringResource(R.string.home_trending),
            onBackClick = onBackClick,
            backContentDescription = stringResource(R.string.cd_back),
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
    val baseContext = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedContext = remember(baseContext, appLanguage) {
        baseContext.withLanguage(appLanguage)
    }

    AppTrendingFilterButton(
        selectedFilter = selectedFilter,
        filters = TrendingFilter.entries,
        selectedLabel = localizedContext.getString(selectedFilter.titleResId),
        label = { filter -> localizedContext.getString(filter.titleResId) },
        onFilterSelected = onFilterClick,
        modifier = modifier,
    )
}

class TrendingAnimeViewModel(
    private val repository: HomeDataRepository,
    private val context: Context,
) : ViewModel() {
    private val presenter = TrendingPresenter(TrendingAnimeUiState(isLoading = true))
    val uiState: StateFlow<TrendingAnimeUiState> = presenter.state

    init {
        load()
    }

    fun selectFilter(filter: TrendingFilter) {
        if (filter == presenter.state.value.selectedFilter) return
        presenter.setState(TrendingAnimeUiState(
            isLoading = true,
            selectedFilter = filter,
        ))
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedFilter = presenter.state.value.selectedFilter
            presenter.update {
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
                    filterTypeAlias = selectedFilter.typeAlias,
                )
            }
                .onSuccess { items ->
                    presenter.setState(TrendingAnimeUiState(
                        isLoading = false,
                        selectedFilter = selectedFilter,
                        items = items,
                        canLoadMore = items.size >= TRENDING_PAGE_LIMIT,
                    ))
                }
                .onFailure { throwable ->
                    presenter.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: context.getString(R.string.trending_error_title),
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val currentState = presenter.state.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            val offset = presenter.state.value.items.size
            val selectedFilter = presenter.state.value.selectedFilter
            presenter.update {
                it.copy(
                    isLoadingMore = true,
                    loadMoreError = null,
                )
            }
            runCatching {
                repository.loadTrendingPage(
                    offset = offset,
                    limit = TRENDING_PAGE_LIMIT,
                    filterTypeAlias = selectedFilter.typeAlias,
                )
            }
                .onSuccess { nextItems ->
                    presenter.update { state ->
                        val mergedItems = mergeAnimePreservingOrder(state.items, nextItems)
                        state.copy(
                            isLoadingMore = false,
                            items = mergedItems,
                            canLoadMore = nextItems.size >= TRENDING_PAGE_LIMIT,
                            loadMoreError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    presenter.update {
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
