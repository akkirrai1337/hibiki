package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.activeExternalSourceRegistry
import org.akkirrai.beakokit.model.CatalogCapabilities

/** Platform-owned external repository services. The caller owns and must close the HTTP client. */
class ExternalSourceRepositoryPlatform(
    val coordinator: ExternalSourceRepositoryCoordinator,
    private val activePackageLoaderFactory: (SourceId) -> ActiveExternalSourcePackageLoader,
    private val closeResources: () -> Unit,
) {
    fun loadActivePackage(sourceId: SourceId): ActiveExternalSourcePackage? =
        activePackageLoaderFactory(sourceId).load()

    /** Builds the inactive external registry without changing the built-in registry. */
    fun loadActiveRegistry(
        sourceIds: Iterable<SourceId>,
        catalogCapabilities: (SourceManifest) -> CatalogCapabilities,
        runtimeFactory: ExternalSourceRuntimeFactory,
    ): ExternalSourceRegistry = activeExternalSourceRegistry(
        packages = sourceIds.distinct().mapNotNull(::loadActivePackage),
        catalogCapabilities = catalogCapabilities,
        runtimeFactory = runtimeFactory,
    )

    fun close() = closeResources()
}
