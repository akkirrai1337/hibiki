package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

/** Keeps the built-in catalog active while routing installed external sources in the background. */
class TransitionalAnimeCatalogRepository(
    private val builtIn: AnimeCatalogRepository,
    private val external: ExternalSourceCatalogRepository,
) : MultiSourceAnimeCatalogRepository {
    private var selectedSourceId: String? = null

    override val initialItems: List<Anime> = builtIn.initialItems

    override fun canContinuePaginationAfterShortPage(): Boolean =
        selectedSourceId?.let(external::hasSource) == true

    override fun selectSource(sourceId: String) {
        selectedSourceId = sourceId
        if (external.hasSource(sourceId)) {
            external.selectSource(sourceId)
        } else {
            builtIn.selectSource(sourceId)
        }
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime =
        if (isExternalId(id)) external.getDetails(id, fallback) else builtIn.getDetails(id, fallback)

    // builtIn is EmptyAnimeCatalogRepository in production, so delegating unconditionally here
    // (as every other method already avoids doing) would make Home's "recently updated"
    // section always empty. Route through the selected external source the same way search() does.
    override suspend fun latest(limit: Int): List<Anime> =
        selectedSourceId
            ?.takeIf(external::hasSource)
            ?.let { sourceId ->
                external.selectSource(sourceId)
                external.search(
                    AnimeCatalogQuery(pageSize = limit, filters = AnimeSearchFilters(sortAlias = "updated")),
                ).items
            }
            ?: builtIn.latest(limit)

    override suspend fun filterCatalog(): org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog =
        selectedSourceId
            ?.takeIf(external::hasSource)
            ?.let { external.filterCatalog() }
            ?: builtIn.filterCatalog()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
        selectedSourceId
            ?.takeIf(external::hasSource)
            ?.let { sourceId ->
                external.selectSource(sourceId)
                external.search(query)
            }
            ?: builtIn.search(query)

    override suspend fun searchSource(sourceId: String, query: AnimeCatalogQuery): AnimeCatalogPage =
        if (external.hasSource(sourceId)) {
            external.searchSource(sourceId, query)
        } else {
            builtIn.search(query)
        }

    private fun isExternalId(id: String): Boolean =
        org.akkirrai.beakokit.api.AnimeKey.parse(id)?.sourceId?.let { external.hasSource(it.value) } == true
}
