package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.demo.DemoCatalog

/** Platform-neutral catalog boundary consumed by shared screens. */
interface AnimeCatalogRepository {
    val initialItems: List<Anime>

    /**
     * Some external APIs cap a page below the requested size without exposing a total.
     * The catalog may continue requesting pages until the source returns no new items.
     */
    fun canContinuePaginationAfterShortPage(): Boolean = false

    fun selectSource(sourceId: String) = Unit

    suspend fun getDetails(id: String, fallback: Anime): Anime = fallback

    suspend fun latest(limit: Int): List<Anime> = search(
        AnimeCatalogQuery(pageSize = limit.coerceAtLeast(1)),
    ).items

    suspend fun filterCatalog(): AnimeCatalogFilterCatalog = AnimeCatalogFilterCatalog()

    suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage

    suspend fun search(query: String): List<Anime> =
        search(AnimeCatalogQuery(text = query)).items
}

/** Optional catalog capability for source-separated search results. */
interface MultiSourceAnimeCatalogRepository : AnimeCatalogRepository {
    suspend fun searchSource(sourceId: String, query: AnimeCatalogQuery): AnimeCatalogPage
}

data class AnimeCatalogQuery(
    val text: String = "",
    val page: Int = 1,
    val pageSize: Int = 20,
    val filters: AnimeSearchFilters = AnimeSearchFilters(),
) {
    val offset: Int get() = (page.coerceAtLeast(1) - 1) * pageSize.coerceAtLeast(1)
}

data class AnimeCatalogPage(
    val items: List<Anime>,
    val page: Int,
    val canLoadMore: Boolean,
)

/** Temporary deterministic data source for the Windows prototype. */
object PrototypeAnimeCatalogRepository : AnimeCatalogRepository {
    override val initialItems: List<Anime> = DemoCatalog.items

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog = AnimeCatalogFilterCatalog(
        sortOptions = listOf(
            "relevance" to "Relevance",
            "rating" to "Popular",
            "title" to "Alphabetical",
        ).map { (id, title) -> org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption(id, title) },
        genreOptions = initialItems.flatMap { it.genres }
            .distinct()
            .sorted()
            .map { genre -> org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption(genre, genre) },
    )

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
        val filters = query.filters
        val filtered = initialItems.filter { anime ->
            (query.text.isBlank() || anime.title.contains(query.text, ignoreCase = true) ||
                anime.subtitle.contains(query.text, ignoreCase = true) ||
                anime.genres.any { genre -> genre.contains(query.text, ignoreCase = true) }) &&
                (filters.includedGenreAliases.isEmpty() || filters.includedGenreAliases.any { genre ->
                    anime.genres.any { it.equals(genre, ignoreCase = true) }
                }) &&
                filters.excludedGenreAliases.none { genre ->
                    anime.genres.any { it.equals(genre, ignoreCase = true) }
                }
        }
        val pageSize = query.pageSize.coerceAtLeast(1)
        val pageItems = filtered.drop(query.offset).take(pageSize)
        return AnimeCatalogPage(
            items = pageItems,
            page = query.page.coerceAtLeast(1),
            canLoadMore = query.offset + pageItems.size < filtered.size,
        )
    }
}
