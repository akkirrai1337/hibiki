package org.akkirrai.hibiki.shared.source

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceClientVersion
import org.akkirrai.beakokit.api.SourceHostApi
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceRepositoryCatalogLoader
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryLoadSnapshot
import org.akkirrai.beakokit.api.SourceRepositoryUrlResolver

/**
 * Application-level owner of the background external-repository snapshot.
 *
 * This coordinator does not merge the snapshot into the active built-in registry yet. That
 * transition remains a later parity checkpoint, after package runtime integration is verified.
 */
class ExternalSourceRepositoryCoordinator(
    private val catalogLoader: SourceRepositoryCatalogLoader,
    private val repositoryUrlResolver: SourceRepositoryUrlResolver = SourceRepositoryUrlResolver(),
) {
    private val refreshMutex = Mutex()

    private val snapshotState = MutableStateFlow(SourceRepositoryLoadSnapshot(
        loaded = emptyList(),
        failures = emptyList(),
    ))

    /** Latest repository result; remains separate from the active built-in source registry. */
    val snapshot: StateFlow<SourceRepositoryLoadSnapshot> = snapshotState.asStateFlow()

    /** Returns the user-configured repository endpoints without loading their indexes. */
    fun repositories(): List<SourceRepositoryEndpoint> = catalogLoader.repositories()

    /** Adds one repository endpoint; duplicate URLs remain idempotent. */
    fun addRepository(endpoint: SourceRepositoryEndpoint): List<SourceRepositoryEndpoint> =
        catalogLoader.addRepository(endpoint)

    /** Resolves a user-provided repository link before persisting it. */
    fun addRepositoryUrl(input: String): List<SourceRepositoryEndpoint> =
        addRepository(repositoryUrlResolver.resolve(input))

    /** Removes one repository endpoint and evicts only its entries from the loaded snapshot. */
    fun removeRepository(url: String): List<SourceRepositoryEndpoint> {
        val repositories = catalogLoader.removeRepository(url)
        snapshotState.value = snapshot.value.copy(
            loaded = snapshot.value.loaded.filter { it.endpoint.url != url },
            failures = snapshot.value.failures.filter { it.endpoint.url != url },
        )
        return repositories
    }

    /** Resolves a user-provided repository link before removing it. */
    fun removeRepositoryUrl(input: String): List<SourceRepositoryEndpoint> =
        removeRepository(repositoryUrlResolver.resolve(input).url)

    /** IDs claimed by more than one loaded repository. They are never made installable. */
    fun conflictingSourceIds(): Set<SourceId> = snapshot.value.loaded
        .flatMap { repository -> repository.index.sources }
        .groupingBy { manifest -> manifest.sourceId }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys

    /** Source IDs advertised by exactly one successfully loaded repository. */
    fun availableSourceIds(): List<SourceId> = availableSourceManifests().map(SourceManifest::sourceId)

    /** Source manifests advertised by exactly one repository; collisions must be resolved by the user. */
    fun availableSourceManifests(): List<SourceManifest> = snapshot.value.loaded
        .asSequence()
        .flatMap { repository -> repository.index.sources.asSequence() }
        .filterNot { manifest -> manifest.sourceId in conflictingSourceIds() }
        .toList()

    fun availableSourceManifest(sourceId: SourceId): SourceManifest? =
        availableSourceManifests().firstOrNull { manifest -> manifest.sourceId == sourceId }

    suspend fun refresh(
        clientVersion: Int = SourceClientVersion.CURRENT,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryLoadSnapshot {
        return refreshMutex.withLock {
            val previous = snapshot.value
            val refreshed = catalogLoader.loadAll(
                clientVersion = clientVersion,
                supportedSourceApiVersion = supportedSourceApiVersion,
                supportedHostApiVersion = supportedHostApiVersion,
            )
            val freshByUrl = refreshed.loaded.associateBy { it.endpoint.url }
            val previousByUrl = previous.loaded.associateBy { it.endpoint.url }
            val failedUrls = refreshed.failures.mapTo(mutableSetOf()) { it.endpoint.url }
            val mergedLoaded = catalogLoader.repositories().mapNotNull { endpoint ->
                freshByUrl[endpoint.url]
                    ?: previousByUrl[endpoint.url].takeIf { endpoint.url in failedUrls }
            }
            refreshed.copy(loaded = mergedLoaded).also { snapshotState.value = it }
        }
    }

    suspend fun refreshRepository(
        url: String,
        clientVersion: Int = SourceClientVersion.CURRENT,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryLoadSnapshot = refreshMutex.withLock {
        val endpoint = catalogLoader.repositories().firstOrNull { it.url == url }
            ?: return@withLock snapshot.value
        val refreshed = catalogLoader.loadOne(
            endpoint = endpoint,
            clientVersion = clientVersion,
            supportedSourceApiVersion = supportedSourceApiVersion,
            supportedHostApiVersion = supportedHostApiVersion,
        )
        val current = snapshot.value
        val previousLoaded = current.loaded.firstOrNull { it.endpoint.url == url }
        val loaded = current.loaded.filterNot { it.endpoint.url == url } +
            listOfNotNull(refreshed.loaded.firstOrNull() ?: previousLoaded)
        val failures = current.failures
            .filterNot { it.endpoint.url == url } + refreshed.failures
        SourceRepositoryLoadSnapshot(
            loaded = loaded,
            failures = failures,
        ).also { snapshotState.value = it }
    }
}

class ExternalSourceRepositoryConflictException(
    sourceIds: Set<SourceId>,
) : IllegalStateException(
    "Source IDs are published by multiple repositories: " + sourceIds.joinToString { it.value },
)
