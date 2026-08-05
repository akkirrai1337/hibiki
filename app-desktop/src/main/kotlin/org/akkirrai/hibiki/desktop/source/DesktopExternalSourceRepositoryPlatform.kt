package org.akkirrai.hibiki.desktop.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.nio.file.Path
import org.akkirrai.beakokit.api.JvmSourceRepositoryStore
import org.akkirrai.beakokit.api.KtorSourceRepositoryTransport
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryCoordinator
import org.akkirrai.hibiki.shared.source.ExternalSourceRepositoryPlatform

/** Creates Desktop external-source adapters without changing the active built-in registry. */
fun createDesktopExternalSourceRepositoryPlatform(
    storageDirectory: Path,
): ExternalSourceRepositoryPlatform {
    val client = HttpClient(CIO)
    val catalog = SourceRepositoryCatalog(
        JvmSourceRepositoryStore(storageDirectory.resolve("repositories.json")),
    )
    val loader = SourceRepositoryCatalogLoader(
        catalog = catalog,
        loader = SourceRepositoryLoader(
            transport = KtorSourceRepositoryTransport(client),
        ),
    )
    return ExternalSourceRepositoryPlatform(
        coordinator = ExternalSourceRepositoryCoordinator(loader),
        closeResources = client::close,
    )
}
