package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.MetadataReference
import org.akkirrai.hibiki.core.metadata.AggregatorEntryResolver
import org.akkirrai.hibiki.core.metadata.decodeExternalEntryId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.network.AndroidHttpClientFactory
import org.akkirrai.hibiki.core.source.AnimeSearchRepository
import org.akkirrai.hibiki.core.source.AnimeSourceRuntimeManager
import org.akkirrai.hibiki.feature.home.HomeRepository

class CatalogRepository(
    context: Context,
    private val client: HttpClient = AndroidHttpClientFactory.create(),
    sourceManager: AnimeSourceRuntimeManager? = null,
    private val closeClientOnClose: Boolean = true,
    /** Shared with the rest of the app - see HibikiDependencies. Absent only on the standalone paths
     * that build this repository on their own, where an aggregator catalog is simply not offered. */
    private val metadataService: ExternalMetadataService? = null,
) : AggregatorEntryResolver {
    private val appContext = context.applicationContext
    private val sourceManager = sourceManager ?: AnimeSourceRuntimeManager(appContext, client)
    private val searchRepository = AnimeSearchRepository(
        context = appContext,
        client = client,
        sourceManager = this.sourceManager,
        closeClientOnClose = false,
        metadataService = metadataService,
    )
    private val homeRepository = HomeRepository(
        context = appContext,
        client = client,
        sourceManager = this.sourceManager,
        closeClientOnClose = false,
        metadataService = metadataService,
    )
    val cardMetadata: StateFlow<Map<String, Anime>> = searchRepository.cardMetadata
    val recentCardMetadata: StateFlow<Map<String, Anime>> = homeRepository.cardMetadata
    val pendingCardMetadata: StateFlow<Set<String>> = searchRepository.pendingCardMetadata
    val pendingRecentCardMetadata: StateFlow<Set<String>> = homeRepository.pendingCardMetadata

    suspend fun loadPage(
        page: Int = 1,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
        query: String = "",
        sort: CatalogSort = CatalogSort.Popular,
        forceRefresh: Boolean = false,
    ): CatalogPage = coroutineScope {
        val pageIndex = page.coerceAtLeast(1)
        val catalogDeferred = async { searchRepository.getSearchFilterCatalog() }
        val offset = (pageIndex - 1) * CATALOG_PAGE_SIZE
        // A source that supports no real search sort of its own (e.g. AnimeVost, AnimePahe -
        // `supportedSorts` is just RELEVANCE) exposes CatalogSort.Updated as its *only* catalog
        // view, and for those, `latest()` and an empty-query `search()` return the identical
        // listing - confirmed against their JS payloads. Only `search()` takes a real offset
        // though (`latest(limit)` has none, it's a fixed top-N snapshot), so route Updated
        // through it there to get true pagination instead of HomeRepository's capped snapshot.
        // A source with a genuine Popular/Alphabetical sort (AniLiberty, YummyAnime, AnimeGo)
        // hits a distinct "/latest" endpoint for Updated - keep using HomeRepository for those,
        // substituting search() would silently swap that feed for generic search-default order.
        val hasRealSearchSort = if (sort == CatalogSort.Updated) {
            val catalog = catalogDeferred.await()
            catalog.capabilities.supports(AnimeSearchSort.TITLE) ||
                catalog.capabilities.supports(AnimeSearchSort.RATING)
        } else {
            false
        }
        val anime = if (sort == CatalogSort.Updated && hasRealSearchSort) {
            homeRepository.loadRecentlyUpdatedPage(
                offset = offset,
                limit = CATALOG_PAGE_SIZE,
                forceRefresh = forceRefresh,
            ).filter { item -> query.isBlank() || item.title.contains(query, ignoreCase = true) }
        } else {
            searchRepository.search(
                AnimeSearchRequest(
                    query = query,
                    limit = CATALOG_PAGE_SIZE,
                    offset = offset,
                    sort = sort.searchSort,
                    typeAliases = listOfNotNull(filters.typeAlias),
                    statusAliases = listOfNotNull(filters.statusAlias),
                    includedGenreAliases = filters.includedGenreAliases.sorted(),
                    excludedGenreAliases = filters.excludedGenreAliases.sorted(),
                    yearFrom = filters.yearFrom,
                    yearTo = filters.yearTo,
                    sourceFilterValues = filters.sourceFilterValues,
                ),
                allowEmptyQuery = true,
                forceRefresh = forceRefresh,
                enrichCardsWithMetadata = false,
            )
        }

        val catalog = catalogDeferred.await()
        CatalogPage(
            title = "",
            description = null,
            filterCatalog = catalog,
            items = anime.map(::CatalogAnimeCard),
            currentPage = pageIndex,
            canLoadMore = anime.size >= CATALOG_PAGE_SIZE,
        )
    }

    /**
     * Turns a card from that catalog into something playable: the source's own title for this entry.
     *
     * Null means the source does not have it, as far as its own search can tell - the screen says so
     * and offers to look for it by hand, rather than opening a title page that has no episodes.
     */
    override suspend fun resolveEntry(anime: Anime): Anime? {
        val service = metadataService ?: return null
        val (provider, externalId) = decodeExternalEntryId(anime.id) ?: return anime
        val entry = service.entryFor(MetadataReference(provider, externalId)) ?: return null
        val sourceId = sourceManager.selectedId
        val resolved = service.resolveSourceTitle(sourceId.value, entry) { query ->
            runCatching { sourceManager.current().search(query) }.getOrNull()
        } ?: return null
        return searchRepository.getDetails(
            resolved.titleId,
            anime.copy(id = resolved.titleId),
            requireSourceDetails = true,
            bypassCache = true,
        )
    }

    /** Binds an entry to a title of this source by hand, from the resolution sheet, and opens it. */
    override suspend fun bindEntry(anime: Anime, titleId: String): Anime? {
        val service = metadataService ?: return null
        val (provider, externalId) = decodeExternalEntryId(anime.id) ?: return null
        val entry = service.entryFor(MetadataReference(provider, externalId)) ?: return null
        service.setManualSourceTitle(sourceManager.selectedId.value, titleId, entry)
        return searchRepository.getDetails(
            titleId,
            anime.copy(id = titleId),
            requireSourceDetails = true,
            bypassCache = true,
        )
    }

    /** The source's own results for a query, for that sheet to choose from. */
    override suspend fun searchSourceTitles(query: String): List<Anime> =
        searchRepository.search(AnimeSearchRequest(query = query, limit = 20))

    fun close() {
        searchRepository.close()
        homeRepository.close()
        if (closeClientOnClose) client.close()
    }

    private companion object {
        const val CATALOG_PAGE_SIZE = 24
    }
}

data class CatalogPage(
    val title: String,
    val description: String?,
    /** Filter capabilities reported by the source that produced this page. */
    val filterCatalog: AnimeSearchFilterCatalog?,
    val items: List<CatalogAnimeCard>,
    val currentPage: Int,
    val canLoadMore: Boolean,
)

data class CatalogAnimeCard(
    val anime: Anime,
)
