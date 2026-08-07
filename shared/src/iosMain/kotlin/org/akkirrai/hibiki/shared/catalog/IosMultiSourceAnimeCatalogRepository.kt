package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.beakokit.api.InMemorySourceHealthReporter
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.CachingSourceExecutionPolicy
import org.akkirrai.beakokit.api.ResilientSourceExecutionPolicy
import org.akkirrai.beakokit.api.SourceExecutionPolicy
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog

internal class IosMultiSourceAnimeCatalogRepository(
    private val preferEnglish: Boolean = false,
    initialSourceId: String? = null,
) : MultiSourceAnimeCatalogRepository {
    private val knownSourceIds = emptyList<SourceId>()
    private var activeSourceId: SourceId? = knownSourceIds.firstOrNull { it.value == initialSourceId }
    private val repositories = mutableMapOf<SourceId, IosAnimeCatalogRepository>()
    private val sourceHealthReporter = InMemorySourceHealthReporter()
    private val sourceExecutionPolicy: SourceExecutionPolicy = CachingSourceExecutionPolicy(
        delegate = ResilientSourceExecutionPolicy(sourceHealthReporter),
    )

    private fun repositoryFor(sourceId: SourceId): IosAnimeCatalogRepository {
        require(sourceId in knownSourceIds) { "iOS does not include built-in source $sourceId" }
        return repositories.getOrPut(sourceId) {
            IosAnimeCatalogRepository(
                preferEnglish = preferEnglish,
                sourceId = sourceId,
                sourceHealthReporter = sourceHealthReporter,
                sourceExecutionPolicy = sourceExecutionPolicy,
            )
        }
    }

    override val initialItems: List<Anime>
        get() = emptyList()

    override fun selectSource(sourceId: String) {
        knownSourceIds.firstOrNull { it.value == sourceId }?.let { activeSourceId = it }
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime {
        return fallback
    }

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog = AnimeCatalogFilterCatalog()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage = AnimeCatalogPage(
        items = emptyList(),
        page = query.page.coerceAtLeast(1),
        canLoadMore = false,
    )

    override suspend fun searchSource(sourceId: String, query: AnimeCatalogQuery): AnimeCatalogPage =
        AnimeCatalogPage(
            items = emptyList(),
            page = query.page.coerceAtLeast(1),
            canLoadMore = false,
        )

    fun close() {
        repositories.values.forEach(IosAnimeCatalogRepository::close)
        repositories.clear()
    }
}
