package org.akkirrai.hibiki.shared.app.shell.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.presentation.HomePresenter
import org.akkirrai.hibiki.shared.home.state.launchHomeDescriptionEnrichment
import org.akkirrai.hibiki.shared.catalog.model.Anime

internal class HibikiHomeActions(
    val onItemVisible: (Anime) -> Unit,
    val onRefresh: () -> Unit,
)

internal fun createHibikiHomeActions(
    repository: HomeDataRepository?,
    presenter: HomePresenter,
    requests: MutableSet<String>,
    scope: CoroutineScope,
    setHomeState: (org.akkirrai.hibiki.shared.home.state.HomeUiState) -> Unit,
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
