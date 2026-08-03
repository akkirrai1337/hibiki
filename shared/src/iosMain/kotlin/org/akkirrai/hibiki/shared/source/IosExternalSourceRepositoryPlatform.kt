package org.akkirrai.hibiki.shared.source

import kotlinx.cinterop.ExperimentalForeignApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSHomeDirectory
import org.akkirrai.beakokit.api.IosSourceRepositoryStore
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.IosSourcePackageActivationStore
import org.akkirrai.beakokit.api.IosSourcePackageManifestReader
import org.akkirrai.beakokit.api.IosDownloadedSourcePackageExtractor
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
fun createIosExternalSourceRepositoryPlatform(): ExternalSourceRepositoryPlatform {
    val client = HttpClient(Darwin)
    val activationStore = IosSourcePackageActivationStore()
    val packageValidator = SourcePackageValidator(clientVersion = SourceClientVersion.CURRENT)
    val packageInstallationFactory = SourcePackageInstallationCoordinatorFactory(
        downloadService = SourcePackageDownloadService(
            transport = KtorSourcePackageTransport(client),
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = packageValidator,
                sha256 = org.akkirrai.beakokit.api.IosSourcePackageSha256,
            ),
        ),
        extractor = IosDownloadedSourcePackageExtractor(),
        packageValidator = packageValidator,
        activationStoreFactory = SourcePackageActivationStoreFactory { activationStore },
    )
    val fileManager = NSFileManager.defaultManager
    val packageRoot = NSHomeDirectory() + "/Library/Application Support/beakokit/source-packages"
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
                manifestReader = IosSourcePackageManifestReader(),
            )
        },
        packageInstallationFactory = packageInstallationFactory,
        stagingPathFactory = { sourceId ->
            val sourceRoot = "$packageRoot/${sourceId.value}"
            if (!fileManager.fileExistsAtPath(sourceRoot)) {
                check(fileManager.createDirectoryAtPath(
                    path = sourceRoot,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )) { "Unable to create iOS source package directory" }
            }
            "$sourceRoot/package-${NSUUID().UUIDString}"
        },
        activationRepositoryFactory = { sourceId ->
            SourcePackageActivationRepository(sourceId, activationStore)
        },
        closeResources = client::close,
    )
}
