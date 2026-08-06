package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.hibiki.shared.catalog.model.Anime

/** Keeps the built-in catalog active while routing installed external sources in the background. */
class TransitionalAnimeCatalogRepository(
    private val builtIn: AnimeCatalogRepository,
    private val external: ExternalSourceCatalogRepository,
) : MultiSourceAnimeCatalogRepository {
    private var selectedSourceId: String? = null

    override val initialItems: List<Anime> = builtIn.initialItems

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

    override suspend fun latest(limit: Int): List<Anime> = builtIn.latest(limit)

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
