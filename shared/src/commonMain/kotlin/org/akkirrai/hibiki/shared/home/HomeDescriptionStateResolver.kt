package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

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

private fun SearchUiState.applyDescriptionUpdates(updates: Map<String, Anime>): SearchUiState = when (this) {
    is SearchUiState.Content -> copy(items = items.applyDescriptionUpdates(updates))
    else -> this
}
