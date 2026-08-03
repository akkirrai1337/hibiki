package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.source.ExternalAnimeStatusLabels
import org.akkirrai.hibiki.shared.source.toAppAnime

/** Shared catalog adapter for installed external sources during the transition period. */
class ExternalSourceCatalogRepository(
    private val registryProvider: () -> ExternalSourceRegistry?,
    private val contextProvider: (SourceId) -> SourceContext,
    private val statusLabels: ExternalAnimeStatusLabels,
    initialSourceId: SourceId? = null,
) : MultiSourceAnimeCatalogRepository {
    private var selectedSourceId: SourceId? = initialSourceId

    override val initialItems: List<Anime> = emptyList()

    override fun selectSource(sourceId: String) {
        selectedSourceId = SourceId(sourceId)
    }

    override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
        searchSource(requireSelectedSource(), query)

    override suspend fun searchSource(
        sourceId: String,
        query: AnimeCatalogQuery,
    ): AnimeCatalogPage = searchSource(SourceId(sourceId), query)

    private suspend fun searchSource(
        sourceId: SourceId,
        query: AnimeCatalogQuery,
    ): AnimeCatalogPage {
        val source = source(sourceId)
        val request = AnimeSearchRequest(
            query = query.text,
            limit = query.pageSize.coerceAtLeast(1),
            offset = query.offset,
        )
        val items = source.search(request).map { title ->
            title.toAppAnime(
                sourceId = sourceId,
                preferEnglish = SourceLanguage.ENGLISH in contextProvider(sourceId).preferredLanguages,
                statusLabels = statusLabels,
            )
        }
        return AnimeCatalogPage(
            items = items,
            page = query.page.coerceAtLeast(1),
            canLoadMore = items.size >= request.limit,
        )
    }

    override suspend fun getDetails(id: String, fallback: Anime): Anime {
        val key = AnimeKey.parse(id)
        val sourceId = key?.sourceId ?: requireSelectedSource()
        val title = source(sourceId).getById(key?.takeIf { it.sourceId == sourceId }?.nativeId ?: id)
        return title.toAppAnime(
            sourceId = sourceId,
            preferEnglish = SourceLanguage.ENGLISH in contextProvider(sourceId).preferredLanguages,
            statusLabels = statusLabels,
        )
    }

    private fun requireSelectedSource(): SourceId =
        selectedSourceId ?: error("An external source must be selected before searching")

    private fun source(sourceId: SourceId) =
        registryProvider()?.create(sourceId, contextProvider(sourceId))
            ?: error("External source is not installed: $sourceId")
}
