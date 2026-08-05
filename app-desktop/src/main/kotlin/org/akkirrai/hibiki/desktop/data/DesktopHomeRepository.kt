package org.akkirrai.hibiki.desktop.data

import org.akkirrai.hibiki.shared.home.data.HomeDataRepository
import org.akkirrai.hibiki.shared.home.state.HomeUiState
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

/** Desktop network adapter for the shared Home contract. */
class DesktopHomeRepository(
    private val catalogRepository: DesktopCatalogRepository,
) : HomeDataRepository {
    override fun fallbackHomeState(): HomeUiState = HomeUiState()

    override suspend fun refreshHomeState(): HomeUiState = loadHomeState()

    override suspend fun loadHomeState(): HomeUiState {
        val page = catalogRepository.search(
            AnimeCatalogQuery(
                pageSize = HOME_PAGE_SIZE,
                filters = AnimeSearchFilters(sortAlias = "updated"),
            ),
        )
        return HomeUiState(
            trending = page.items,
            popular = page.items,
            recentlyUpdated = page.items,
            canLoadMoreTrending = page.canLoadMore,
            canLoadMoreRecentUpdates = page.canLoadMore,
        )
    }

    override suspend fun search(query: String): List<Anime> = search(
        query = query,
        filters = AnimeSearchFilters(),
        limit = HOME_PAGE_SIZE,
        offset = 0,
    )

    override suspend fun search(
        query: String,
        filters: AnimeSearchFilters,
        limit: Int,
        offset: Int,
    ): List<Anime> = catalogRepository.search(
        AnimeCatalogQuery(
            text = query,
            pageSize = limit,
            page = offset / limit.coerceAtLeast(1) + 1,
            filters = filters,
        ),
    ).items

    override suspend fun getSearchFilterCatalog(): AnimeCatalogFilterCatalog =
        catalogRepository.filterCatalog()

    override suspend fun loadRecentlyUpdatedPage(offset: Int, limit: Int): List<Anime> = search(
        query = "",
        filters = AnimeSearchFilters(sortAlias = "updated"),
        limit = limit,
        offset = offset,
    )

    override suspend fun loadTrendingPage(
        offset: Int,
        limit: Int,
        filterTypeAlias: String?,
    ): List<Anime> = search(
        query = "",
        filters = AnimeSearchFilters(
            typeAlias = filterTypeAlias,
            sortAlias = "popular",
        ),
        limit = limit,
        offset = offset,
    )

    override suspend fun loadRandomAnime(excludedIds: Set<String>): Anime? = loadTrendingPage(
        offset = 0,
        limit = HOME_PAGE_SIZE,
    ).firstOrNull { it.id !in excludedIds }

    override suspend fun enrichDescriptions(items: List<Anime>): List<Anime> = items.map { anime -> enrichDescription(anime) }

    override suspend fun enrichDescription(anime: Anime): Anime =
        catalogRepository.getDetails(anime.id, anime)

    override fun close() = catalogRepository.close()

    private companion object {
        const val HOME_PAGE_SIZE = 12
    }
}
