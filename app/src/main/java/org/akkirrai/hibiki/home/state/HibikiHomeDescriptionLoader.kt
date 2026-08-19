package org.akkirrai.hibiki.home.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.home.state.applyDescriptionUpdates
import org.akkirrai.hibiki.catalog.model.Anime

internal fun launchHomeDescriptionEnrichment(
    anime: Anime,
    repository: HomeDataRepository?,
    presenter: HomePresenter,
    requests: MutableSet<String>,
    scope: CoroutineScope,
) {
    val targetRepository = repository ?: return
    if (!anime.description.isNullOrBlank() || !requests.add(anime.id)) return

    scope.launch {
        val enriched = runCatching { targetRepository.enrichDescription(anime) }.getOrNull()
        requests.remove(anime.id)
        if (repository !== targetRepository || enriched?.description.isNullOrBlank()) return@launch
        presenter.update { state ->
            state.applyDescriptionUpdates(mapOf(enriched.id to enriched))
        }
    }
}
