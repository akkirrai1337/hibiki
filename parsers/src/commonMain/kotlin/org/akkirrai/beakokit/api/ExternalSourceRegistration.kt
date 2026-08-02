package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.CatalogCapabilities

/** Metadata and runtime factory for one installed external source. */
data class ExternalSourceRegistration(
    val info: SourceInfo,
    val catalogCapabilities: CatalogCapabilities,
    val runtimeFactory: (SourceContext) -> ExternalSourceRuntime,
    val registrationOrder: Int? = null,
) {
    fun catalogEntry(): SourceCatalogEntry = SourceCatalogEntry(
        info = info,
        registrationOrder = registrationOrder,
        factory = SourceFactory { context ->
            RuntimeBackedAnimeSource(
                info = info,
                catalogCapabilities = catalogCapabilities,
                runtime = runtimeFactory(context),
            )
        },
    )
}

fun externalSourceCatalog(
    registrations: Iterable<ExternalSourceRegistration>,
): SourceCatalog = SourceCatalog(registrations.map(ExternalSourceRegistration::catalogEntry))
