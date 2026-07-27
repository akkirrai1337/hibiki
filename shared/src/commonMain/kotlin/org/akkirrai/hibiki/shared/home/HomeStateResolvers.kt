package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.SearchUiState

/** True when Home should render search results instead of the feed. */
val HomeUiState.isSearchActive: Boolean
    get() = searchQuery.isNotBlank() || searchResult !is SearchUiState.Idle

/** True when Home has enough feed data to avoid replacing it with loading/error state. */
val HomeUiState.hasFeedContent: Boolean
    get() = continueAnime != null || recentlyWatched.isNotEmpty() || recentlyAddedToLibrary.isNotEmpty()
