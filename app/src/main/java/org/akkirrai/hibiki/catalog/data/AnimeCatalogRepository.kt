package org.akkirrai.hibiki.catalog

import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog

/** Platform-neutral catalog boundary consumed by shared screens. */
interface AnimeCatalogRepository {
    val initialItems: List<Anime>

    fun invalidate() = Unit

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

/** Empty catalog used as a safe default for previews/tests that don't need real search results. */
object EmptyAnimeCatalogRepository : AnimeCatalogRepository {
    override val initialItems: List<Anime> = emptyList()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage = AnimeCatalogPage(
        items = emptyList(),
        page = query.page.coerceAtLeast(1),
        canLoadMore = false,
    )
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
