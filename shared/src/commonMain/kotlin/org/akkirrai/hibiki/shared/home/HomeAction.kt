package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

/** User intents emitted by the shared Home UI and handled by a platform host. */
sealed interface HomeAction {
    data object Refresh : HomeAction
    data class SearchQueryChanged(val query: String) : HomeAction
    data object ClearSearch : HomeAction
    data class ApplySearchFilters(val filters: AnimeSearchFilters) : HomeAction
    data object ResetSearchFilters : HomeAction
    data object LoadMoreSearchResults : HomeAction
    data object LoadMoreTrending : HomeAction
    data object LoadMoreRecentUpdates : HomeAction
    data object OpenRandomAnime : HomeAction
    data class EnrichDescription(val anime: Anime) : HomeAction
}
