package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.animeresolver.model.AnimeSearchRequest
import org.akkirrai.animeresolver.model.AnimeSearchSort
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSearchRepository

class CatalogRepository(
    context: Context,
    client: HttpClient = AndroidHttpClientFactory.create(),
) {
    private val appContext = context.applicationContext
    private val searchRepository = AnimeSearchRepository(appContext, client)

    suspend fun loadPage(
        page: Int = 1,
        categories: List<CatalogCategory> = emptyList(),
    ): CatalogPage {
        val pageIndex = page.coerceAtLeast(1)
        val catalog = searchRepository.getSearchFilterCatalog()
        val items = searchRepository.search(
            AnimeSearchRequest(
                query = "",
                limit = CATALOG_PAGE_SIZE,
                offset = (pageIndex - 1) * CATALOG_PAGE_SIZE,
                sort = AnimeSearchSort.TITLE,
                includedGenreAliases = categories.map(CatalogCategory::genreAlias),
            )
        )

        return CatalogPage(
            title = "",
            description = null,
            categories = catalog.genreOptions.map { option ->
                CatalogCategory(
                    title = option.title,
                    genreAlias = option.id,
                )
            },
            items = items.map(::CatalogAnimeCard),
            currentPage = pageIndex,
            canLoadMore = items.size >= CATALOG_PAGE_SIZE,
        )
    }

    fun close() {
        searchRepository.close()
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
