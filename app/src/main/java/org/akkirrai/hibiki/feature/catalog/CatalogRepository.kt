package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.feature.home.HomeRepository

class CatalogRepository(
    context: Context,
    client: HttpClient = AndroidHttpClientFactory.create(),
) {
    private val appContext = context.applicationContext
    private val searchRepository = AnimeSearchRepository(appContext, client)
    private val homeRepository = HomeRepository(appContext)

    suspend fun loadPage(
        page: Int = 1,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
        query: String = "",
        sort: CatalogSort = CatalogSort.Popular,
    ): CatalogPage {
        val pageIndex = page.coerceAtLeast(1)
        val catalog = searchRepository.getSearchFilterCatalog()
        val offset = (pageIndex - 1) * CATALOG_PAGE_SIZE
        // A source that supports no real search sort of its own (e.g. AnimeVost, AnimePahe -
        // `supportedSorts` is just RELEVANCE) exposes CatalogSort.Updated as its *only* catalog
        // view, and for those, `latest()` and an empty-query `search()` return the identical
        // listing - confirmed against their JS payloads. Only `search()` takes a real offset
        // though (`latest(limit)` has none, it's a fixed top-N snapshot), so route Updated
        // through it there to get true pagination instead of HomeRepository's capped snapshot.
        // A source with a genuine Popular/Alphabetical sort (AniLiberty, YummyAnime, AnimeGo)
        // hits a distinct "/latest" endpoint for Updated - keep using HomeRepository for those,
        // substituting search() would silently swap that feed for generic search-default order.
        val hasRealSearchSort = catalog.capabilities.supports(AnimeSearchSort.TITLE) ||
            catalog.capabilities.supports(AnimeSearchSort.RATING)
        val anime = if (sort == CatalogSort.Updated && hasRealSearchSort) {
            homeRepository.loadRecentlyUpdatedPage(
                offset = offset,
                limit = CATALOG_PAGE_SIZE,
            ).filter { item -> query.isBlank() || item.title.contains(query, ignoreCase = true) }
        } else {
            searchRepository.search(
                AnimeSearchRequest(
                    query = query,
                    limit = CATALOG_PAGE_SIZE,
                    offset = offset,
                    sort = sort.searchSort,
                    typeAliases = listOfNotNull(filters.typeAlias),
                    statusAliases = listOfNotNull(filters.statusAlias),
                    includedGenreAliases = filters.includedGenreAliases.sorted(),
                    excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                    yearFrom = filters.yearFrom,
                    yearTo = filters.yearTo,
                ),
                allowEmptyQuery = true,
            )
        }

        return CatalogPage(
            title = "",
            description = null,
            filterCatalog = catalog,
            items = anime.map(::CatalogAnimeCard),
            currentPage = pageIndex,
            canLoadMore = anime.size >= CATALOG_PAGE_SIZE,
        )
    }

    suspend fun enrichDescription(anime: Anime): Anime =
        searchRepository.getDetails(anime.id, anime)

    fun close() {
        searchRepository.close()
        homeRepository.close()
    }

    private companion object {
        const val CATALOG_PAGE_SIZE = 24
    }
}

data class CatalogPage(
    val title: String,
    val description: String?,
    val filterCatalog: AnimeSearchFilterCatalog,
    val items: List<CatalogAnimeCard>,
    val currentPage: Int,
    val canLoadMore: Boolean,
)

data class CatalogAnimeCard(
    val anime: Anime,
)
