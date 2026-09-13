package org.akkirrai.hibiki.core.metadata

import org.akkirrai.beakokit.metadata.ANILIST_GENRES
import org.akkirrai.beakokit.metadata.ExternalCatalogRequest
import org.akkirrai.beakokit.metadata.ExternalMetadataPreferences
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.FILTERABLE_CATALOG_PROVIDERS
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.canBrowseProviders
import org.akkirrai.beakokit.metadata.metadataProviderOrder
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.AnimeSearchSort
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.CatalogFeature
import org.akkirrai.beakokit.model.SearchFilterOption
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor

/**
 * The common provider-order + batch-browse logic shared by anything that offers an aggregator
 * catalog view instead of a source's own listing (the Catalog screen, and Home's rows and search).
 */
object AggregatorCatalogBrowser {
    /** Which providers may answer for the given source, or null when none may. */
    fun providerOrder(appPreferences: AppPreferences, descriptor: AnimeSourceDescriptor): List<MetadataProviderId>? {
        val preferences = appPreferences.state.value
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

    /** Whether any provider allowed here can apply catalog filters - see FILTERABLE_CATALOG_PROVIDERS. */
    fun canFilter(order: List<MetadataProviderId>): Boolean = order.any { it in FILTERABLE_CATALOG_PROVIDERS }

    /**
     * What the aggregator's catalog can be narrowed by: AniList's own vocabulary, the same on every
     * source, since it is AniList's list being filtered and not the source's. Sorts and features are
     * exactly the two catalog modes the aggregator serves - Popular and Updated - so the Catalog
     * screen's sort correction has nothing to bounce between.
     */
    fun filterCatalog(): AnimeSearchFilterCatalog = AnimeSearchFilterCatalog(
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

    /** A batch of aggregator entries turned into [Anime], or null when the aggregator cannot answer. */
    suspend fun browse(
        service: ExternalMetadataService?,
        order: List<MetadataProviderId>?,
        mode: ExternalCatalogRequest.Mode,
        offset: Int,
        limit: Int,
        preferEnglish: Boolean,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
        query: String? = null,
    ): List<Anime>? {
        val svc = service ?: return null
        val resolvedOrder = order ?: return null
        if (!canBrowseProviders(resolvedOrder)) return null
        val request = ExternalCatalogRequest(
            mode = mode,
            offset = offset,
            limit = limit,
            genres = filters.includedGenreAliases.toList(),
            excludedGenres = filters.excludedGenreAliases.toList(),
            types = listOfNotNull(filters.typeAlias),
            statuses = listOfNotNull(filters.statusAlias),
            yearFrom = filters.yearFrom,
            yearTo = filters.yearTo,
            query = query?.trim()?.takeIf { it.isNotEmpty() },
        )
        val (entries, provider) = runCatching { svc.browse(request, resolvedOrder) }.getOrNull() ?: return null
        // No provider answered at all: an outage, or none of the allowed ones can apply these filters.
        if (provider == null) return null
        // An empty feed nobody narrowed is a provider with nothing to page through, and the caller falls
        // back to the source's own listing. An empty filtered or searched one is a real answer - nothing
        // matches - and falling back there would show unrelated titles as if they did.
        if (entries.isEmpty() && !request.hasFilters && request.query == null) return null
        return entries.map { it.toCatalogAnime(preferEnglish) }
    }

    /**
     * [browse] for an arbitrary `offset`/`limit` window rather than a provider-aligned page.
     *
     * AniList pages by number, so an offset has to be a multiple of the page size - which Home's search
     * is not: it asks for one more than it shows (to know whether there is more) and then steps by what
     * it showed. Asking AniList for that offset directly returns the first page again, and "load more"
     * never gets past it. So this fetches whole provider pages covering the window and slices it out.
     * Null only when the very first page has no answer at all.
     */
    suspend fun browseRange(
        service: ExternalMetadataService?,
        order: List<MetadataProviderId>?,
        mode: ExternalCatalogRequest.Mode,
        offset: Int,
        limit: Int,
        preferEnglish: Boolean,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
        query: String? = null,
    ): List<Anime>? {
        val firstPage = offset / RANGE_PAGE_SIZE
        val skip = offset - firstPage * RANGE_PAGE_SIZE
        val collected = mutableListOf<Anime>()
        var page = firstPage
        while (collected.size < skip + limit) {
            val batch = browse(service, order, mode, page * RANGE_PAGE_SIZE, RANGE_PAGE_SIZE, preferEnglish, filters, query)
                ?: if (page == firstPage) return null else break
            collected += batch
            if (batch.size < RANGE_PAGE_SIZE) break
            page++
        }
        return collected.drop(skip).take(limit)
    }

    private const val RANGE_PAGE_SIZE = 25
}
