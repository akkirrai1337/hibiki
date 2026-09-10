package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.metadata.ExternalCatalogRequest
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.beakokit.metadata.ExternalMetadataPreferences
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.MetadataReference
import org.akkirrai.beakokit.metadata.canBrowseProviders
import org.akkirrai.beakokit.metadata.metadataProviderOrder
import org.akkirrai.hibiki.core.metadata.decodeExternalEntryId
import org.akkirrai.hibiki.core.metadata.toCatalogAnime
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
) {
    private val appContext = context.applicationContext
    private val appPreferences = AppPreferences(appContext)
    private val sourceManager = sourceManager ?: AnimeSourceRuntimeManager(appContext, client)
    private val searchRepository = AnimeSearchRepository(
        context = appContext,
        client = client,
        sourceManager = this.sourceManager,
        closeClientOnClose = false,
    )
    private val homeRepository = HomeRepository(
        context = appContext,
        client = client,
        sourceManager = this.sourceManager,
        closeClientOnClose = false,
    )

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
                ),
                allowEmptyQuery = true,
                forceRefresh = forceRefresh,
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
     * A page of the aggregator's own catalog, when the user has asked to browse that instead of the
     * source's.
     *
     * Cards here name provider entries, not titles of a source, so nothing is resolved while
     * browsing: a resolution is a search against the source, and doing one per visible card would
     * spend two dozen requests to answer a question about the one card that gets clicked. Opening
     * one goes through [resolveEntry].
     *
     * Alphabetical has no aggregator equivalent - none of them sorts a catalog by name - so that
     * mode keeps the source's own catalog and this returns null for it.
     */
    suspend fun loadAggregatorPage(page: Int, sort: CatalogSort): CatalogPage? {
        val service = metadataService ?: return null
        val order = providerOrder() ?: return null
        if (!canBrowseProviders(order)) return null
        val mode = when (sort) {
            CatalogSort.Popular -> ExternalCatalogRequest.Mode.POPULAR
            CatalogSort.Updated -> ExternalCatalogRequest.Mode.TRENDING
            CatalogSort.Alphabetical -> return null
        }
        val pageIndex = page.coerceAtLeast(1)
        val browsed = service.browse(
            ExternalCatalogRequest(
                mode = mode,
                offset = (pageIndex - 1) * CATALOG_PAGE_SIZE,
                limit = CATALOG_PAGE_SIZE,
            ),
            order,
        )
        val entries = browsed.first
        val preferEnglish = searchRepository.prefersEnglishTitles()
        return CatalogPage(
            title = "",
            description = null,
            // An aggregator has no per-source filter options to offer, and the screen
            // hides its filter controls when the lists are empty.
            filterCatalog = AnimeSearchFilterCatalog(),
            items = entries.map { CatalogAnimeCard(it.toCatalogAnime(preferEnglish)) },
            currentPage = pageIndex,
            canLoadMore = entries.size >= CATALOG_PAGE_SIZE,
        )
    }

    /**
     * Turns a card from that catalog into something playable: the source's own title for this entry.
     *
     * Null means the source does not have it, as far as its own search can tell - the screen says so
     * and offers to look for it by hand, rather than opening a title page that has no episodes.
     */
    suspend fun resolveEntry(anime: Anime): Anime? {
        val service = metadataService ?: return null
        val (provider, externalId) = decodeExternalEntryId(anime.id) ?: return anime
        val entry = service.entryFor(MetadataReference(provider, externalId)) ?: return null
        val sourceId = sourceManager.selectedId
        val resolved = service.resolveSourceTitle(sourceId.value, entry) { query ->
            runCatching { sourceManager.current().search(query) }.getOrNull()
        } ?: return null
        return searchRepository.getDetails(resolved.titleId, anime.copy(id = resolved.titleId))
    }

    /** Binds an entry to a title of this source by hand, from the resolution sheet, and opens it. */
    suspend fun bindEntry(anime: Anime, titleId: String): Anime? {
        val service = metadataService ?: return null
        val (provider, externalId) = decodeExternalEntryId(anime.id) ?: return null
        val entry = service.entryFor(MetadataReference(provider, externalId)) ?: return null
        service.setManualSourceTitle(sourceManager.selectedId.value, titleId, entry)
        return searchRepository.getDetails(titleId, anime.copy(id = titleId))
    }

    /** The source's own results for a query, for that sheet to choose from. */
    suspend fun searchSourceTitles(query: String): List<Anime> =
        searchRepository.search(AnimeSearchRequest(query = query, limit = 20))

    /** Which providers may answer for the current source, or null when none may. */
    private fun providerOrder(): List<MetadataProviderId>? {
        val preferences = appPreferences.state.value
        val descriptor = sourceManager.current().descriptor
        val order = metadataProviderOrder(
            ExternalMetadataPreferences(
                enabled = preferences.externalMetadataEnabled,
                overrides = preferences.externalMetadataOverrides,
                provider = preferences.externalMetadataProvider,
                fallbackEnabled = preferences.externalMetadataFallback,
            ),
            descriptor.id.value,
            descriptor.info.useExternalMetadata,
        )
        return order.takeIf { it.isNotEmpty() }
    }

    suspend fun enrichDescription(anime: Anime): Anime =
        searchRepository.getDetails(anime.id, anime)

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
    val filterCatalog: AnimeSearchFilterCatalog,
    val items: List<CatalogAnimeCard>,
    val currentPage: Int,
    val canLoadMore: Boolean,
)

data class CatalogAnimeCard(
    val anime: Anime,
)
