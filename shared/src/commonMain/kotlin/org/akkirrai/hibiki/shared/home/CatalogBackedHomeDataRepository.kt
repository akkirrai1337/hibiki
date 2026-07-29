package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

/** Home data adapter for hosts that already expose the shared catalog and library contracts. */
class CatalogBackedHomeDataRepository(
    private val catalogRepository: AnimeCatalogRepository,
    private val libraryRepository: LibraryRepository,
) : HomeDataRepository {
    override fun fallbackHomeState(): HomeUiState = HomeUiState()

    override suspend fun refreshHomeState(): HomeUiState = loadHomeState()

    override suspend fun loadHomeState(): HomeUiState {
        val libraryEntries = libraryRepository.getEntries()
        return HomeUiState(
            recentlyAddedToLibrary = libraryEntries
                .filter { it.category != org.akkirrai.hibiki.shared.library.LibraryCategory.Saved }
                .sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
                .map { it.anime },
        )
    }

    override suspend fun search(query: String): List<Anime> =
        search(query, AnimeSearchFilters(), limit = 20, offset = 0)

    override suspend fun search(
        query: String,
        filters: AnimeSearchFilters,
        limit: Int,
        offset: Int,
    ): List<Anime> = catalogRepository.search(
        AnimeCatalogQuery(
            text = query,
            page = (offset / limit.coerceAtLeast(1)) + 1,
            pageSize = limit,
            filters = filters,
        ),
    ).items

    override suspend fun getSearchFilterCatalog(): AnimeCatalogFilterCatalog =
        catalogRepository.filterCatalog()

    override suspend fun loadRecentlyUpdatedPage(offset: Int, limit: Int): List<Anime> =
        catalogRepository.search(
            AnimeCatalogQuery(
                page = (offset / limit.coerceAtLeast(1)) + 1,
                pageSize = limit,
                filters = AnimeSearchFilters(sortAlias = "updated"),
            ),
        ).items

    override suspend fun loadTrendingPage(
        offset: Int,
        limit: Int,
        filterTypeAlias: String?,
    ): List<Anime> = catalogRepository.search(
        AnimeCatalogQuery(
            page = (offset / limit.coerceAtLeast(1)) + 1,
            pageSize = limit,
            filters = AnimeSearchFilters(sortAlias = "popular", typeAlias = filterTypeAlias),
        ),
    ).items

    override suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? =
        catalogRepository.search(AnimeCatalogQuery(pageSize = 1)).items.firstOrNull { it.id !in excludedIds }

    override suspend fun enrichDescriptions(items: List<Anime>): List<Anime> = items.map { enrichDescription(it) }

    override suspend fun enrichDescription(anime: Anime): Anime =
        catalogRepository.getDetails(anime.id, anime)

    override fun close() = Unit
}
