package org.akkirrai.hibiki.shared.source

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.akkirrai.beakokit.api.KtorSourceRepositoryTransport
import org.akkirrai.beakokit.api.SourceRepositoryCatalog
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoader

/** Creates Android external-source adapters without changing the active built-in registry. */
fun createAndroidExternalSourceRepositoryPlatform(
    context: Context,
): ExternalSourceRepositoryPlatform {
    val client = HttpClient(OkHttp)
    val catalog = SourceRepositoryCatalog(AndroidSourceRepositoryStore(context))
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
