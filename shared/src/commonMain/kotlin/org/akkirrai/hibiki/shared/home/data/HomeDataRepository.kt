package org.akkirrai.hibiki.shared.home.data

import org.akkirrai.hibiki.shared.home.state.HomeUiState

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

/** Platform-neutral Home data contract. Implementations provide network and persistence details. */
interface HomeDataRepository {
    fun fallbackHomeState(): HomeUiState

    suspend fun refreshHomeState(): HomeUiState

    suspend fun loadHomeState(): HomeUiState

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
