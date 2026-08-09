package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.api.IosSourceRepositoryStore
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.IosSourcePackageActivationStore
import org.akkirrai.beakokit.api.IosSourcePackageManifestReader
import org.akkirrai.beakokit.api.IosDownloadedSourcePackageExtractor
import org.akkirrai.beakokit.api.IosSourcePackageStorage
import org.akkirrai.beakokit.api.KtorSourceRepositoryTransport
import org.akkirrai.beakokit.api.KtorSourcePackageTransport
import org.akkirrai.beakokit.api.SourcePackageActivationRepository
import org.akkirrai.beakokit.api.SourcePackageActivationStoreFactory
import org.akkirrai.beakokit.api.SourcePackageArtifactVerifier
import org.akkirrai.beakokit.api.SourcePackageDownloadService
import org.akkirrai.beakokit.api.SourcePackageInstallationCoordinatorFactory
import org.akkirrai.beakokit.api.SourcePackageValidator
import org.akkirrai.beakokit.api.SourceClientVersion
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader

/** Creates the iOS adapters without changing the active built-in source registry. */
@OptIn(ExperimentalForeignApi::class)
fun createIosExternalSourceRepositoryPlatform(
    packageStorage: IosSourcePackageStorage = IosSourcePackageStorage(),
): ExternalSourceRepositoryPlatform {
    val client = HttpClient(Darwin)
    val activationStore = IosSourcePackageActivationStore(
        packagePathValidator = packageStorage::requireManagedPackagePath,
    )
    packageStorage.removeUnreferencedPackages(activationStore.activePackagePaths())
    val packageValidator = SourcePackageValidator(clientVersion = SourceClientVersion.CURRENT)
    val packageInstallationFactory = SourcePackageInstallationCoordinatorFactory(
        downloadService = SourcePackageDownloadService(
            transport = KtorSourcePackageTransport(client),
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = packageValidator,
                sha256 = org.akkirrai.beakokit.api.IosSourcePackageSha256,
            ),
        ),
        extractor = IosDownloadedSourcePackageExtractor(storage = packageStorage),
        packageValidator = packageValidator,
        activationStoreFactory = SourcePackageActivationStoreFactory { activationStore },
    )
    val catalog = SourceRepositoryCatalog(IosSourceRepositoryStore())
    val loader = SourceRepositoryCatalogLoader(
        catalog = catalog,
        loader = SourceRepositoryLoader(
            transport = KtorSourceRepositoryTransport(client),
        ),
    )
    return ExternalSourceRepositoryPlatform(
        coordinator = ExternalSourceRepositoryCoordinator(loader),
        activePackageLoaderFactory = { sourceId ->
            ActiveExternalSourcePackageLoader(
                activationRepository = SourcePackageActivationRepository(sourceId, activationStore),
                manifestReader = IosSourcePackageManifestReader(storage = packageStorage),
            )
        },
        packageInstallationFactory = packageInstallationFactory,
        stagingPathFactory = packageStorage::newStagingPath,
        activationRepositoryFactory = { sourceId ->
            SourcePackageActivationRepository(sourceId, activationStore)
        },
        packageCleanup = { packageStorage.removePackage(it.packagePath) },
        closeResources = client::close,
    )
}
