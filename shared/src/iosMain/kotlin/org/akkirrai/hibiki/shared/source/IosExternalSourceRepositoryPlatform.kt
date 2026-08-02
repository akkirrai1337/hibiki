package org.akkirrai.hibiki.shared.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.api.IosSourceRepositoryStore
import org.akkirrai.beakokit.api.ActiveExternalSourcePackageLoader
import org.akkirrai.beakokit.api.IosSourcePackageActivationStore
import org.akkirrai.beakokit.api.IosSourcePackageManifestReader
import org.akkirrai.beakokit.api.KtorSourceRepositoryTransport
import org.akkirrai.beakokit.api.SourcePackageActivationRepository
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader

/** Creates the iOS adapters without changing the active built-in source registry. */
fun createIosExternalSourceRepositoryPlatform(): ExternalSourceRepositoryPlatform {
    val client = HttpClient(Darwin)
    val activationStore = IosSourcePackageActivationStore()
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
        closeResources = client::close,
    )
}
