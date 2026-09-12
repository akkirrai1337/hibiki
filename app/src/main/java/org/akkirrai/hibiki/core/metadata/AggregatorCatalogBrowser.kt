package org.akkirrai.hibiki.core.metadata

import org.akkirrai.beakokit.metadata.ExternalCatalogRequest
import org.akkirrai.beakokit.metadata.ExternalMetadataPreferences
import org.akkirrai.beakokit.metadata.ExternalMetadataService
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.beakokit.metadata.canBrowseProviders
import org.akkirrai.beakokit.metadata.metadataProviderOrder
import org.akkirrai.hibiki.app.settings.AppPreferences
import org.akkirrai.hibiki.core.model.Anime
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
    ): List<Anime>? {
        val svc = service ?: return null
        val resolvedOrder = order ?: return null
        if (!canBrowseProviders(resolvedOrder)) return null
        val entries = runCatching { svc.browse(ExternalCatalogRequest(mode, offset, limit), resolvedOrder) }
            .getOrNull()?.first ?: return null
        if (entries.isEmpty()) return null
        return entries.map { it.toCatalogAnime(preferEnglish) }
    }
}
