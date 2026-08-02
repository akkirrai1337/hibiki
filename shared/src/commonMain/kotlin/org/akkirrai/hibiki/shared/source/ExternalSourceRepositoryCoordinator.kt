package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryLoadSnapshot

/**
 * Application-level owner of the background external-repository snapshot.
 *
 * This coordinator does not merge the snapshot into the active built-in registry yet. That
 * transition remains a later parity checkpoint, after package runtime integration is verified.
 */
class ExternalSourceRepositoryCoordinator(
    private val catalogLoader: SourceRepositoryCatalogLoader,
) {
    var snapshot: SourceRepositoryLoadSnapshot = SourceRepositoryLoadSnapshot(
        loaded = emptyList(),
        failures = emptyList(),
    )
        private set

    suspend fun refresh(
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryLoadSnapshot {
        return catalogLoader.loadAll(
            clientVersion = clientVersion,
            supportedSourceApiVersion = supportedSourceApiVersion,
            supportedHostApiVersion = supportedHostApiVersion,
        ).also { snapshot = it }
    }
}
