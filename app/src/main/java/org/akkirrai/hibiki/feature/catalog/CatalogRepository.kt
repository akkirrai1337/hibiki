package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.hibiki.core.model.Anime
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
        categories: List<CatalogCategory> = emptyList(),
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
                    includedGenreAliases = categories.map(CatalogCategory::genreAlias),
                )
            )
        }

        return CatalogPage(
            title = "",
            description = null,
            categories = catalog.genreOptions.map { option ->
                CatalogCategory(
                    title = option.title,
                    genreAlias = option.id,
                )
            },
            items = anime.map(::CatalogAnimeCard),
            currentPage = pageIndex,
            canLoadMore = anime.size >= CATALOG_PAGE_SIZE,
        )
    }

    fun close() {
        searchRepository.close()
        homeRepository.close()
    }

    private companion object {
        const val CATALOG_PAGE_SIZE = 60
    }
}

data class CatalogPage(
    val title: String,
    val description: String?,
    val categories: List<CatalogCategory>,
    val items: List<CatalogAnimeCard>,
    val currentPage: Int,
    val canLoadMore: Boolean,
)

data class CatalogCategory(
    val title: String,
    val genreAlias: String,
)

data class CatalogAnimeCard(
    val anime: Anime,
)
