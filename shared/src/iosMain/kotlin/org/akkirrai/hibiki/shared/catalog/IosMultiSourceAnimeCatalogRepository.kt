package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.beakokit.api.InMemorySourceHealthReporter
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.source.BuiltInSources
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog

internal class IosMultiSourceAnimeCatalogRepository(
    private val preferEnglish: Boolean = false,
    initialSourceId: String? = null,
) : AnimeCatalogRepository {
    private val knownSourceIds = listOf(
        BuiltInSources.YUMMY_ANIME_ID,
        BuiltInSources.ANI_LIBERTY_ID,
    )
    private var activeSourceId: SourceId = knownSourceIds.firstOrNull { it.value == initialSourceId }
        ?: BuiltInSources.ANI_LIBERTY_ID
    private val repositories = mutableMapOf<SourceId, IosAnimeCatalogRepository>()
    private val sourceHealthReporter = InMemorySourceHealthReporter()

    private val activeRepository: IosAnimeCatalogRepository
        get() = repositoryFor(activeSourceId)

    private fun repositoryFor(sourceId: SourceId): IosAnimeCatalogRepository {
        require(sourceId in knownSourceIds) { "iOS does not include source $sourceId" }
        return repositories.getOrPut(sourceId) {
            IosAnimeCatalogRepository(
                preferEnglish = preferEnglish,
                sourceId = sourceId,
                sourceHealthReporter = sourceHealthReporter,
            )
        }
    }

    override val initialItems: List<Anime>
        get() = activeRepository.initialItems

    override fun selectSource(sourceId: String) {
        knownSourceIds.firstOrNull { it.value == sourceId }?.let { activeSourceId = it }
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime {
        val sourceId = AnimeKey.parse(id)?.sourceId ?: activeSourceId
        return repositoryFor(sourceId).getDetails(id, fallback)
    }

    override suspend fun filterCatalog(): AnimeCatalogFilterCatalog = activeRepository.filterCatalog()

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage = activeRepository.search(query)

    fun close() {
        repositories.values.forEach(IosAnimeCatalogRepository::close)
        repositories.clear()
    }
}
