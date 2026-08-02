package org.akkirrai.hibiki.shared.source

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.KtorSourceRepositoryTransport
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.JvmSourcePackageActivationStore
import org.akkirrai.beakokit.api.JvmSourcePackageManifestReader
import org.akkirrai.beakokit.api.JvmDownloadedSourcePackageExtractor
import org.akkirrai.beakokit.api.JvmSourcePackageSha256
import org.akkirrai.beakokit.api.SourcePackageActivationRepository
import org.akkirrai.beakokit.api.SourcePackageInstallationCoordinatorFactory
import org.akkirrai.beakokit.api.SourcePackageDownloadService
import org.akkirrai.beakokit.api.SourcePackageArtifactVerifier
import org.akkirrai.beakokit.api.SourcePackageValidator
import org.akkirrai.beakokit.api.SourceClientVersion
import org.akkirrai.beakokit.api.KtorSourcePackageTransport
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader

/** Creates Android external-source adapters without changing the active built-in registry. */
fun createAndroidExternalSourceRepositoryPlatform(
    context: Context,
    clientVersion: Int = SourceClientVersion.CURRENT,
): ExternalSourceRepositoryPlatform {
    val client = HttpClient(OkHttp)
    val activationStore = JvmSourcePackageActivationStore(
        rootDirectory = context.filesDir.toPath().resolve("beakokit/source-state"),
    )
    val catalog = SourceRepositoryCatalog(AndroidSourceRepositoryStore(context))
    val loader = SourceRepositoryCatalogLoader(
        catalog = catalog,
        loader = SourceRepositoryLoader(
            transport = KtorSourceRepositoryTransport(client),
        ),
    )
    val packageValidator = SourcePackageValidator(clientVersion = clientVersion)
    val packageInstallationFactory = SourcePackageInstallationCoordinatorFactory(
        downloadService = SourcePackageDownloadService(
            transport = KtorSourcePackageTransport(client),
            artifactVerifier = SourcePackageArtifactVerifier(
                validator = packageValidator,
                sha256 = JvmSourcePackageSha256,
            ),
        ),
        extractor = JvmDownloadedSourcePackageExtractor(),
        packageValidator = packageValidator,
        activationStoreFactory = { activationStore },
    )
    return ExternalSourceRepositoryPlatform(
        coordinator = ExternalSourceRepositoryCoordinator(loader),
        activePackageLoaderFactory = { sourceId ->
            ActiveExternalSourcePackageLoader(
                activationRepository = SourcePackageActivationRepository(sourceId, activationStore),
                manifestReader = JvmSourcePackageManifestReader(),
            )
        },
        packageInstallationFactory = packageInstallationFactory,
        closeResources = client::close,
    )
}
