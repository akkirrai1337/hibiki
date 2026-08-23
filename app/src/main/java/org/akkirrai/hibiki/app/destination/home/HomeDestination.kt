package org.akkirrai.hibiki.app.destination.home

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.home.state.HomeUiState
import org.akkirrai.hibiki.home.state.launchHomeDescriptionEnrichment
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

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

internal data class AppDestinationHomeState(
    val ui: HomeUiState,
    val search: HomeSearchUiState,
    val listState: LazyListState,
)
