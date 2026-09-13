package org.akkirrai.hibiki.feature.catalog

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.metadata.ExternalCatalogRequest
import org.akkirrai.beakokit.metadata.ANILIST_GENRES
import org.akkirrai.beakokit.metadata.FILTERABLE_CATALOG_PROVIDERS
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.MetadataReference
import org.akkirrai.hibiki.core.metadata.AggregatorCatalogBrowser
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
    suspend fun loadAggregatorPage(
        page: Int,
        sort: CatalogSort,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
    ): CatalogPage? {
        val order = providerOrder() ?: return null
        val mode = when (sort) {
            CatalogSort.Popular -> ExternalCatalogRequest.Mode.POPULAR
            CatalogSort.Updated -> ExternalCatalogRequest.Mode.TRENDING
            CatalogSort.Alphabetical -> return null
        }
        val pageIndex = page.coerceAtLeast(1)
        val preferEnglish = searchRepository.prefersEnglishTitles()
        // A provider outage or an exhausted/unsupported feed must not become a successful empty
        // screen. Returning null activates the existing source-catalog fallback in the ViewModel.
        val entries = AggregatorCatalogBrowser.browse(
            service = metadataService,
            order = order,
            mode = mode,
            offset = (pageIndex - 1) * CATALOG_PAGE_SIZE,
            limit = CATALOG_PAGE_SIZE,
            preferEnglish = preferEnglish,
            filters = filters,
        ) ?: return null
        return CatalogPage(
            title = "",
            description = null,
            // Never `AnimeSearchFilterCatalog()`: that defaults to CatalogCapabilities.FULL, which told
            // the screen this catalog offered every sort, while the source's own page offered fewer.
            // The screen corrects a selected sort its catalog does not offer, so each page flipped the
            // correction to the other catalog's fallback and loaded it, which flipped it back - an
            // endless reload (seen live on Anichi with Kitsu metadata). The catalog below declares
            // exactly the two modes this page can serve instead. Without a provider that can filter,
            // there is no opinion to give, and null keeps whatever the source's own page said.
            filterCatalog = aggregatorFilterCatalog().takeIf { order.any { it in FILTERABLE_CATALOG_PROVIDERS } },
            items = entries.map { CatalogAnimeCard(it) },
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
        )
    }

    /** The source's own results for a query, for that sheet to choose from. */
    override suspend fun searchSourceTitles(query: String): List<Anime> =
        searchRepository.search(AnimeSearchRequest(query = query, limit = 20))

    /** Which providers may answer for the current source, or null when none may. */
    private fun providerOrder(): List<MetadataProviderId>? =
        AggregatorCatalogBrowser.providerOrder(appPreferences, sourceManager.current().descriptor)

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

/**
 * What the aggregator's catalog can be narrowed by: AniList's own vocabulary, the same on every
 * source, since it is AniList's list being filtered and not the source's. Sorts and features are
 * exactly the two modes [CatalogRepository.loadAggregatorPage] serves - Popular and Updated.
 */
private fun aggregatorFilterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
    typeOptions = listOf("tv", "movie", "ova", "ona", "special").map { SearchFilterOption(it, it) },
    statusOptions = listOf("ongoing" to "Ongoing", "released" to "Released", "announced" to "Announced")
        .map { (id, title) -> SearchFilterOption(id, title) },
    genreOptions = ANILIST_GENRES.map { SearchFilterOption(it, it) },
    capabilities = CatalogCapabilities(
        supportedSorts = setOf(AnimeSearchSort.RATING),
        supportedFilters = setOf(
            AnimeSearchFilter.TYPE,
            AnimeSearchFilter.STATUS,
            AnimeSearchFilter.INCLUDED_GENRES,
            AnimeSearchFilter.EXCLUDED_GENRES,
            AnimeSearchFilter.YEAR_RANGE,
        ),
        features = setOf(CatalogFeature.LATEST_RELEASES),
        fallbackSort = AnimeSearchSort.RATING,
    ),
)

data class CatalogPage(
    val title: String,
    val description: String?,
    /** Null when the page's producer has no capability opinion of its own - an aggregator page, whose
     * caller keeps whatever the source's own page reported. See loadAggregatorPage. */
    val filterCatalog: AnimeSearchFilterCatalog?,
    val items: List<CatalogAnimeCard>,
    val currentPage: Int,
    val canLoadMore: Boolean,
)

data class CatalogAnimeCard(
    val anime: Anime,
)
