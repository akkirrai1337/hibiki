package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.SourceId

/** Platform-owned external repository services. The caller owns and must close the HTTP client. */
class ExternalSourceRepositoryPlatform(
    val coordinator: ExternalSourceRepositoryCoordinator,
    private val activePackageLoaderFactory: (SourceId) -> ActiveExternalSourcePackageLoader,
    private val closeResources: () -> Unit,
) {
    fun loadActivePackage(sourceId: SourceId): ActiveExternalSourcePackage? =
        activePackageLoaderFactory(sourceId).load()

    fun close() = closeResources()
}
