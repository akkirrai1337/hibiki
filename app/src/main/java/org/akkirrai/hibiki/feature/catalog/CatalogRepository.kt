package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.feature.home.HomeRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterOption

class CatalogRepository(
    context: Context,
    client: HttpClient = AndroidHttpClientFactory.create(),
) : AnimeCatalogRepository {
    private val appContext = context.applicationContext
    private val searchRepository = AnimeSearchRepository(appContext, client)
    private val homeRepository = HomeRepository(appContext)

    override val initialItems: List<Anime> = emptyList()

    suspend fun loadPage(
        page: Int = 1,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
        query: String = "",
        sort: CatalogSort = CatalogSort.Popular,
    ): CatalogPage {
        val pageIndex = page.coerceAtLeast(1)
        val catalog = searchRepository.getSearchFilterCatalog()
        val offset = (pageIndex - 1) * CATALOG_PAGE_SIZE
        val anime = when (sort) {
            CatalogSort.Updated -> homeRepository.loadRecentlyUpdatedPage(
                offset = offset,
                limit = CATALOG_PAGE_SIZE,
            ).filter { item -> query.isBlank() || item.title.contains(query, ignoreCase = true) }
            else -> searchRepository.search(
                AnimeSearchRequest(
                    query = query,
                    limit = CATALOG_PAGE_SIZE,
                    offset = offset,
                    sort = when (sort) {
                        CatalogSort.Alphabetical -> AnimeSearchSort.TITLE
                        CatalogSort.Popular -> AnimeSearchSort.RATING
                        CatalogSort.Updated -> error("Handled above")
                    },
                    typeAliases = listOfNotNull(filters.typeAlias),
                    statusAliases = listOfNotNull(filters.statusAlias),
                    includedGenreAliases = filters.includedGenreAliases.sorted(),
                    excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                    yearFrom = filters.yearFrom,
                    yearTo = filters.yearTo,
                )
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

    override suspend fun getDetails(id: String, fallback: Anime): Anime =
        searchRepository.getDetails(id, fallback)

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog =
        searchRepository.getSearchFilterCatalog().toSharedCatalog()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val page = loadPage(
            page = query.page,
            filters = query.filters,
            query = query.text,
            sort = query.filters.sortAlias.toCatalogSort(),
        )
        return AnimeCatalogPage(
            items = page.items.map(CatalogAnimeCard::anime),
            page = page.currentPage,
            canLoadMore = page.canLoadMore,
        )
    }

    fun close() {
        searchRepository.close()
        homeRepository.close()
    }

    private companion object {
        const val CATALOG_PAGE_SIZE = 50
    }
}

private fun String.toCatalogSort(): CatalogSort = when (lowercase()) {
    "alphabetical", "title" -> CatalogSort.Alphabetical
    "updated", "latest", "latest_releases" -> CatalogSort.Updated
    else -> CatalogSort.Popular
}

private fun AnimeSearchFilterCatalog.toSharedCatalog(): AnimeCatalogFilterCatalog {
    val sortAliases = capabilities.supportedSorts
        .map { it.name.lowercase() }
        .toMutableSet()
    if (CatalogFeature.LATEST_RELEASES in capabilities.features) sortAliases += "updated"

    return AnimeCatalogFilterCatalog(
        sortOptions = sortOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
        typeOptions = typeOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
        statusOptions = statusOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
        genreOptions = genreOptions.map { AnimeCatalogFilterOption(it.id, it.title) },
        capabilities = AnimeCatalogCapabilities(
            supportedSorts = sortAliases,
            supportedFilters = capabilities.supportedFilters.mapNotNull { filter ->
                when (filter) {
                    org.akkirrai.beakokit.model.AnimeSearchFilter.TYPE -> AnimeCatalogFilter.TYPE
                    org.akkirrai.beakokit.model.AnimeSearchFilter.STATUS -> AnimeCatalogFilter.STATUS
                    org.akkirrai.beakokit.model.AnimeSearchFilter.INCLUDED_GENRES -> AnimeCatalogFilter.INCLUDED_GENRES
                    org.akkirrai.beakokit.model.AnimeSearchFilter.EXCLUDED_GENRES -> AnimeCatalogFilter.EXCLUDED_GENRES
                    org.akkirrai.beakokit.model.AnimeSearchFilter.YEAR_RANGE -> AnimeCatalogFilter.YEAR_RANGE
                }
            }.toSet(),
        ),
    )
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
