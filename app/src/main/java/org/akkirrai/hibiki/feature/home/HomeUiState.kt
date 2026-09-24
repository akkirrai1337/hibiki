package org.akkirrai.hibiki.feature.home

import org.akkirrai.hibiki.core.model.Anime

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasContinueHistory: Boolean? = null,
    val featuredAnime: List<Anime> = emptyList(),
    val continueAnime: Anime? = null,
    val recentlyWatched: List<Anime> = emptyList(),
    val popular: List<Anime> = emptyList(),
    val trending: List<Anime> = emptyList(),
    val isRandomLoading: Boolean = false,
    val recentlyUpdated: List<Anime> = emptyList(),
    val isRecentUpdatesLoadingMore: Boolean = false,
    val canLoadMoreRecentUpdates: Boolean = true,
    val recentUpdatesLoadMoreError: String? = null,
)
