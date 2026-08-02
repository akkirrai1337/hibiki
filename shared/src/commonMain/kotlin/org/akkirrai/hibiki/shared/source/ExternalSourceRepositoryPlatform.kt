package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourcePackageInstallationCoordinator
import org.akkirrai.beakokit.api.SourcePackageInstallationCoordinatorFactory
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourcePackageActivationRepository
import org.akkirrai.beakokit.api.activeExternalSourceRegistry
import org.akkirrai.beakokit.model.CatalogCapabilities

/** Platform-owned external repository services. The caller owns and must close the HTTP client. */
class ExternalSourceRepositoryPlatform(
    val coordinator: ExternalSourceRepositoryCoordinator,
    private val activePackageLoaderFactory: (SourceId) -> ActiveExternalSourcePackageLoader,
    private val packageInstallationFactory: SourcePackageInstallationCoordinatorFactory? = null,
    private val stagingPathFactory: ((SourceId) -> String)? = null,
    private val activationRepositoryFactory: ((SourceId) -> SourcePackageActivationRepository)? = null,
    private val closeResources: () -> Unit,
) {
    fun loadActivePackage(sourceId: SourceId): ActiveExternalSourcePackage? =
        activePackageLoaderFactory(sourceId).load()

    fun createPackageInstallationCoordinator(sourceId: SourceId): SourcePackageInstallationCoordinator =
        requireNotNull(packageInstallationFactory) {
            "Source package installation is not available on this platform"
        }.create(sourceId)

    /** Installs the manifest currently advertised by the loaded repository snapshot. */
    suspend fun installAvailablePackage(
        sourceId: SourceId,
        stagingPath: String,
        initialize: suspend () -> Unit,
    ) = coordinator.availableSourceManifest(sourceId)?.let { manifest ->
        createPackageInstallationCoordinator(sourceId).install(
            repositoryManifest = manifest,
            stagingPath = stagingPath,
            initialize = initialize,
        )
    } ?: throw SourcePackageStateException(
        "Source package is not advertised by a loaded repository: $sourceId",
    )

    /** Installs an advertised package using a platform-owned unique staging path. */
    suspend fun installAvailablePackage(
        sourceId: SourceId,
        initialize: suspend () -> Unit,
    ) = installAvailablePackage(
        sourceId = sourceId,
        stagingPath = requireNotNull(stagingPathFactory) {
            "Automatic source package staging is not available on this platform"
        }(sourceId),
        initialize = initialize,
    )

    /** Rolls back one source to its previously activated package version. */
    fun rollbackActivePackage(sourceId: SourceId) =
        requireNotNull(activationRepositoryFactory) {
            "Source package rollback is not available on this platform"
        }(sourceId).rollback()

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

    /** Builds the inactive registry from the latest successfully loaded repository snapshot. */
    fun loadAvailableActiveRegistry(
        catalogCapabilities: (SourceManifest) -> CatalogCapabilities,
        runtimeFactory: ExternalSourceRuntimeFactory,
    ): ExternalSourceRegistry = loadActiveRegistry(
        sourceIds = coordinator.availableSourceIds(),
        catalogCapabilities = catalogCapabilities,
        runtimeFactory = runtimeFactory,
    )

    fun close() = closeResources()
}
