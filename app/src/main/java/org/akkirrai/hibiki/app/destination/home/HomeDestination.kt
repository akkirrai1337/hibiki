package org.akkirrai.hibiki.app.destination.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.catalog.sort.CatalogSort
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.home.screen.HomeScreen
import org.akkirrai.hibiki.home.state.HomeUiState
import org.akkirrai.hibiki.home.state.launchHomeDescriptionEnrichment
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryEntry
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.source.AppSourceDescriptor

internal class HibikiHomeActions(
    val onItemVisible: (Anime) -> Unit,
    val onRefresh: () -> Unit,
)

internal fun createHibikiHomeActions(
    repository: HomeDataRepository?,
    presenter: HomePresenter,
    requests: MutableSet<String>,
    scope: CoroutineScope,
    setHomeState: (HomeUiState) -> Unit,
): HibikiHomeActions = HibikiHomeActions(
    onItemVisible = { anime ->
        launchHomeDescriptionEnrichment(
            anime = anime,
            repository = repository,
            presenter = presenter,
            requests = requests,
            scope = scope,
        )
    },
    onRefresh = {
        repository?.let { repo ->
            scope.launch {
                try {
                    presenter.setState(presenter.state.value.copy(isLoading = true))
                    setHomeState(repo.refreshHomeState())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    println("Hibiki home refresh failed: ${throwable.message ?: throwable::class.simpleName}")
                    presenter.setState(presenter.state.value.copy(isLoading = false))
                }
            }
        }
    },
)

internal class HibikiHomeSearchActions(
    presenter: HomeSearchPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onClear: () -> Unit = presenter::clearSearch
    val onFilterApply: (AnimeSearchFilters) -> Unit = presenter::applyFilters
    val onLoadMore: () -> Unit = presenter::loadMore
    val onRetry: () -> Unit = presenter::retrySearch
}

internal data class AppDestinationHomeActions(
    val onQueryChange: (String) -> Unit,
    val onSearchClear: () -> Unit,
    val onFilterApply: (AnimeSearchFilters) -> Unit,
    val onSearchLoadMore: () -> Unit,
    val onSearchRetry: () -> Unit,
    val onItemVisible: (Anime) -> Unit,
    val onRefresh: () -> Unit,
)

internal data class AppDestinationHomeState(
    val ui: HomeUiState,
    val search: HomeSearchUiState,
    val listState: LazyListState,
)

@Composable
internal fun ColumnScope.HomeDestinationRoute(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    sourcesById: Map<String, AppSourceDescriptor>,
    libraryEntries: List<LibraryEntry>,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    onQueryChange: (String) -> Unit,
    homeSearchState: HomeSearchUiState,
    onFilterApply: (AnimeSearchFilters) -> Unit,
    onSearchClear: () -> Unit,
    onSearchLoadMore: () -> Unit,
    onSearchRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onRetry: () -> Unit,
    onLoadMoreRetry: () -> Unit,
    onSortSelected: (CatalogSort) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    baseHomeState: HomeUiState,
    onItemVisible: (Anime) -> Unit,
    onHomeRefresh: () -> Unit,
    bottomContentPadding: Dp,
) {
    HomeScreen(
        state = state,
        listState = listState,
        sourcesById = sourcesById,
        libraryEntries = libraryEntries,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        onQueryChange = onQueryChange,
        homeSearchState = homeSearchState,
        onFilterApply = onFilterApply,
        onSearchClear = onSearchClear,
        onSearchLoadMore = onSearchLoadMore,
        onSearchRetry = onSearchRetry,
        onAnimeClick = onAnimeClick,
        onRetry = onRetry,
        onLoadMoreRetry = onLoadMoreRetry,
        onSortSelected = onSortSelected,
        onBrowseCatalog = onBrowseCatalog,
        onOpenLibrary = onOpenLibrary,
        baseHomeState = baseHomeState,
        onItemVisible = onItemVisible,
        onHomeRefresh = onHomeRefresh,
        bottomContentPadding = bottomContentPadding,
    )
}
