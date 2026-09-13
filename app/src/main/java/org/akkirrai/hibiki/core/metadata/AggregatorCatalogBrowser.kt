package org.akkirrai.hibiki.core.metadata

import org.akkirrai.beakokit.metadata.ExternalCatalogRequest
import org.akkirrai.beakokit.metadata.ExternalMetadataPreferences
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.canBrowseProviders
import org.akkirrai.beakokit.metadata.metadataProviderOrder
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor

/**
 * The common provider-order + batch-browse logic shared by anything that offers an aggregator
 * catalog view instead of a source's own listing (currently the Catalog screen and Home).
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

    /** A batch of aggregator entries turned into [Anime], or null when the aggregator cannot answer. */
    suspend fun browse(
        service: ExternalMetadataService?,
        order: List<MetadataProviderId>?,
        mode: ExternalCatalogRequest.Mode,
        offset: Int,
        limit: Int,
        preferEnglish: Boolean,
        filters: AnimeSearchFilters = AnimeSearchFilters(),
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
        )
        val (entries, provider) = runCatching { svc.browse(request, resolvedOrder) }.getOrNull() ?: return null
        // No provider answered at all: an outage, or none of the allowed ones can apply these filters.
        if (provider == null) return null
        // An empty unfiltered feed is a provider with nothing to page through, and the caller falls back
        // to the source's own catalog. An empty filtered one is a real answer - nothing matches - and
        // falling back there would show unfiltered titles as if they did.
        if (entries.isEmpty() && !request.hasFilters) return null
        return entries.map { it.toCatalogAnime(preferEnglish) }
    }
}
