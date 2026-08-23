package org.akkirrai.hibiki.home.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.home.data.HomeDataRepository
import org.akkirrai.hibiki.home.presentation.HomePresenter
import org.akkirrai.hibiki.search.model.SearchUiState

fun mergeAnimePreservingOrder(primary: List<Anime>, additions: List<Anime>): List<Anime> =
    (primary + additions).distinctBy(Anime::id)

/** True when Home should render search results instead of the feed. */
val HomeUiState.isSearchActive: Boolean
    get() = searchQuery.isNotBlank() || searchResult !is SearchUiState.Idle

/** True when Home has enough feed data to avoid replacing it with loading/error state. */
val HomeUiState.hasFeedContent: Boolean
    get() = continueAnime != null || recentlyWatched.isNotEmpty() || recentlyAddedToLibrary.isNotEmpty()

enum class HomeSearchBackAction {
    DismissIme,
    ClearSearch,
    None,
}

fun homeSearchBackAction(
    isImeVisible: Boolean,
    isSearchActive: Boolean,
): HomeSearchBackAction = when {
    isImeVisible -> HomeSearchBackAction.DismissIme
    isSearchActive -> HomeSearchBackAction.ClearSearch
    else -> HomeSearchBackAction.None
}

fun List<Anime>.mergeMissingDescriptions(descriptions: Map<String, String>): List<Anime> = map { anime ->
    if (anime.description.isNullOrBlank()) {
        descriptions[anime.id]?.let { description -> anime.copy(description = description) } ?: anime
    } else {
        anime
    }
}

fun List<Anime>.applyDescriptionUpdates(updates: Map<String, Anime>): List<Anime> {
    var changed = false
    val updatedItems = map { anime ->
        updates[anime.id]?.also { changed = true } ?: anime
    }
    return if (changed) updatedItems else this
}

fun HomeUiState.applyDescriptionUpdates(updates: Map<String, Anime>): HomeUiState {
    val updatedFeatured = featuredAnime.applyDescriptionUpdates(updates)
    val updatedTrending = trending.applyDescriptionUpdates(updates)
    val updatedRecent = recentlyUpdated.applyDescriptionUpdates(updates)
    val updatedSearchResult = searchResult.applyDescriptionUpdates(updates)
    return if (
        updatedFeatured === featuredAnime &&
        updatedTrending === trending &&
        updatedRecent === recentlyUpdated &&
        updatedSearchResult === searchResult
    ) this else copy(
        featuredAnime = updatedFeatured,
        trending = updatedTrending,
        recentlyUpdated = updatedRecent,
        searchResult = updatedSearchResult,
    )
}

fun HomeUiState.preserveLoadedDescriptions(previous: HomeUiState): HomeUiState {
    val descriptions = (previous.featuredAnime + previous.trending + previous.recentlyUpdated)
        .mapNotNull { anime -> anime.description?.takeIf(String::isNotBlank)?.let { anime.id to it } }
        .toMap()
    if (descriptions.isEmpty()) return this
    return copy(
        featuredAnime = featuredAnime.mergeMissingDescriptions(descriptions),
        trending = trending.mergeMissingDescriptions(descriptions),
        recentlyUpdated = recentlyUpdated.mergeMissingDescriptions(descriptions),
    )
}

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

private fun SearchUiState.applyDescriptionUpdates(updates: Map<String, Anime>): SearchUiState = when (this) {
    is SearchUiState.Content -> copy(items = items.applyDescriptionUpdates(updates))
    else -> this
}
