package org.akkirrai.hibiki.home.data

import org.akkirrai.hibiki.home.state.HomeUiState

import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

/** Platform-neutral Home data contract. Implementations provide network and persistence details. */
interface HomeDataRepository {
    suspend fun refreshHomeState(): HomeUiState

    suspend fun loadHomeState(): HomeUiState

    /**
     * Only the locally-derived sections (continue watching, recently added to library) --
     * cheap, no network involved. Lets a caller paint real data immediately on cold start
     * instead of waiting on [loadHomeState]'s catalog calls for content that never depended
     * on them in the first place.
     */
    suspend fun loadLocalHomeState(): HomeUiState

    suspend fun search(query: String): List<Anime>

    suspend fun search(
        query: String,
        filters: AnimeSearchFilters,
        limit: Int,
        offset: Int,
    ): List<Anime>

    suspend fun getSearchFilterCatalog(): AnimeCatalogFilterCatalog

    suspend fun loadRecentlyUpdatedPage(offset: Int, limit: Int): List<Anime>

    suspend fun loadTrendingPage(
        offset: Int,
        limit: Int,
        filterTypeAlias: String? = null,
    ): List<Anime>

    suspend fun loadRandomAnime(excludedIds: Set<String>): Anime?

    suspend fun enrichDescriptions(items: List<Anime>): List<Anime>

    suspend fun enrichDescription(anime: Anime): Anime

    fun close()
}
