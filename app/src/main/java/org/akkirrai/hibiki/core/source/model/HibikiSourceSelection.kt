package org.akkirrai.hibiki.core.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.home.presentation.HomeSearchPresenter

internal fun launchSourceSelection(
    sourceId: String,
    repository: AnimeCatalogRepository,
    presenter: AnimeCatalogPresenter,
    homeSearchPresenter: HomeSearchPresenter,
    sourceSearchPresenter: SourcesSearchPresenter,
    homeRepository: HomeDataRepository?,
    homePresenter: HomePresenter,
    scope: CoroutineScope,
    onSourceSelected: (String) -> Unit,
    setHomeState: (org.akkirrai.hibiki.home.state.HomeUiState) -> Unit,
): Job? {
    repository.selectSource(sourceId)
    // Publish the selection before starting any presenter work. The catalog and
    // filter refreshes are asynchronous, but the selected-source indicator and
    // source-dependent UI must update immediately on the first tap.
    onSourceSelected(sourceId)
    presenter.clear()
    presenter.loadFilterCatalog()
    presenter.search()
    homeSearchPresenter.resetForSource()
    sourceSearchPresenter.clear()

    val repositoryForHome = homeRepository ?: return null
    return scope.launch {
        try {
            homePresenter.update { it.copy(isLoading = true, errorMessage = null) }
            setHomeState(repositoryForHome.refreshHomeState())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            homePresenter.update {
                it.copy(isLoading = false, errorMessage = throwable.message ?: "Home loading failed")
            }
        }
    }
}
