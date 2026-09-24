package org.akkirrai.hibiki.feature.search

import org.akkirrai.hibiki.core.model.AnimeSearchFilters

/** Filters chosen on Home, waiting for the search screen they open to pick them up. */
object PendingSearchFilters {
    @Volatile
    private var filters: AnimeSearchFilters? = null

    fun set(value: AnimeSearchFilters) {
        filters = value
    }

    /** Returns the waiting filters once; a later visit to search starts clean. */
    fun take(): AnimeSearchFilters? = filters.also { filters = null }
}
