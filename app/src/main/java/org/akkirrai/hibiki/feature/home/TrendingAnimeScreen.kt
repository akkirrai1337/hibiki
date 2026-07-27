package org.akkirrai.hibiki.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WarningAmber
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.di.hibikiDependencies
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.shared.home.HomeDataRepository
import org.akkirrai.hibiki.shared.home.TrendingAnimeUiState
import org.akkirrai.hibiki.shared.home.TrendingFilter
import org.akkirrai.hibiki.shared.home.TrendingPresenter
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreState
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.shared.home.AppTrendingFilterButton
import org.akkirrai.hibiki.shared.home.AppTrendingContentList
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.PosterPlaceholder
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
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
        org.akkirrai.hibiki.shared.design.component.AppContentState(
            isLoading = state.isLoading,
            hasContent = state.items.isNotEmpty(),
            errorMessage = state.errorMessage,
            errorTitle = stringResource(R.string.trending_error_title),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::load,
            errorIcon = Icons.Outlined.WarningAmber,
            errorIconTint = MaterialTheme.colorScheme.error,
            content = {
                AppTrendingContentList(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    appVerticalAnimeListContent(
                        items = state.items,
                        metaText = { anime -> buildTrendingMeta(anime) },
                        onAnimeClick = onAnimeClick,
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
                    )

                    if (state.isLoadingMore || state.loadMoreError != null) {
                        item(key = "trending_load_more_state") {
                            AppLoadMoreState(
                                isLoading = state.isLoadingMore,
                                errorMessage = state.loadMoreError,
                                errorIcon = Icons.Outlined.WarningAmber,
                                onRetry = viewModel::loadMore,
                            )
                        }
                    }
                }
            },
        )

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
        filterIcon = Icons.Outlined.FilterList,
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
