package org.akkirrai.hibiki.shared.app.shell.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.presentation.SourcesSearchPresenter
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.presentation.HomeSearchPresenter
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.source.launchSourceSelection

internal class HibikiSourceSearchActions(
    presenter: SourcesSearchPresenter,
) {
    val onQueryChange: (String) -> Unit = presenter::onQueryChange
    val onClear: () -> Unit = presenter::clear
    val onRetry: () -> Unit = presenter::search
    val onRetryForSource: (String) -> Unit = presenter::retry
}

internal class HibikiSourceSelectionCoordinator(
    private val repository: AnimeCatalogRepository,
    private val presenter: AnimeCatalogPresenter,
    private val homeSearchPresenter: HomeSearchPresenter,
    private val sourceSearchPresenter: SourcesSearchPresenter,
    private val homeRepository: HomeDataRepository?,
    private val homePresenter: HomePresenter,
    private val scope: CoroutineScope,
    private val onSourceSelected: (String) -> Unit,
    private val setHomeState: (HomeUiState) -> Unit,
    private val setCurrentSourceId: (String) -> Unit,
    private val getJob: () -> Job?,
    private val setJob: (Job?) -> Unit,
) {
    fun select(sourceId: String) {
        setCurrentSourceId(sourceId)
        getJob()?.cancel()
        setJob(
            launchSourceSelection(
                sourceId = sourceId,
                repository = repository,
                presenter = presenter,
                homeSearchPresenter = homeSearchPresenter,
                sourceSearchPresenter = sourceSearchPresenter,
                homeRepository = homeRepository,
                homePresenter = homePresenter,
                scope = scope,
                onSourceSelected = onSourceSelected,
                setHomeState = setHomeState,
            ),
        )
    }

    fun cancel() {
        getJob()?.cancel()
        setJob(null)
    }
}
