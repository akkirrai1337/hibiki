package org.akkirrai.hibiki.shared.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.ExternalSourceRuntimeFactory
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourcePackageActivationState
import org.akkirrai.beakokit.api.SourcePackageStateException
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryLoadSnapshot
import org.akkirrai.beakokit.model.CatalogCapabilities

/** Background owner of repository refreshes and the inactive external registry. */
class ExternalSourceRuntimeCoordinator(
    private val platform: ExternalSourceRepositoryPlatform,
    private val catalogCapabilities: (org.akkirrai.beakokit.api.SourceManifest) -> CatalogCapabilities,
    private val runtimeFactory: ExternalSourceRuntimeFactory,
) {
    private val operationMutex = Mutex()
    private val state = MutableStateFlow(
        ExternalSourceRuntimeSnapshot(
            repository = platform.coordinator.snapshot.value,
            configuredRepositories = platform.coordinator.repositories(),
        ),
    )

    val snapshot: StateFlow<ExternalSourceRuntimeSnapshot> = state.asStateFlow()

    /** Returns the persisted repository endpoints without loading their indexes. */
    suspend fun repositories(): List<SourceRepositoryEndpoint> = operationMutex.withLock {
        platform.coordinator.repositories()
    }

    /** Returns the active package for a source, or null when it has not been installed. */
    suspend fun activePackage(sourceId: SourceId): ActiveExternalSourcePackage? =
        operationMutex.withLock {
            runCatching { platform.loadActivePackage(sourceId) }
                .getOrNull()
        }

    /** Refreshes repositories and replaces only the inactive external registry on success. */
    suspend fun refresh() = operationMutex.withLock {
        refreshLocked()
    }

    /** Adds one repository and refreshes only the inactive external registry. */
    suspend fun addRepository(endpoint: SourceRepositoryEndpoint) = operationMutex.withLock {
        platform.coordinator.addRepository(endpoint)
        updateConfiguredRepositories()
        refreshLocked()
    }

    /** Removes one repository and refreshes only the inactive external registry. */
    suspend fun removeRepository(url: String) = operationMutex.withLock {
        platform.coordinator.removeRepository(url)
        updateConfiguredRepositories()
        refreshLocked()
    }

    private suspend fun refreshLocked(): SourceRepositoryLoadSnapshot {
        try {
            val repository = platform.coordinator.refresh()
            replaceRegistry(repository)
            return repository
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            state.value = state.value.copy(
                repository = platform.coordinator.snapshot.value,
                configuredRepositories = platform.coordinator.repositories(),
                error = error,
            )
            throw error
        }
    }

    /** Installs a repository-advertised package and refreshes only the inactive registry. */
    suspend fun installAvailablePackage(
        sourceId: SourceId,
        initialize: suspend () -> Unit,
    ): SourcePackageActivationState = operationMutex.withLock {
        var activation: SourcePackageActivationState? = null
        try {
            activation = platform.installAvailablePackage(sourceId, initialize)
            rebuildActiveRegistry()
            activation
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            activation?.active?.let { candidate ->
                runCatching {
                    if (activation.previous != null) {
                        platform.rollbackActivePackage(sourceId)
                    } else {
                        platform.deactivateFirstPackage(sourceId, candidate)
                    }
                }.onFailure(error::addSuppressed)
            }
            state.value = state.value.copy(error = error)
            throw error
        }
    }

    /** Rolls back one package and refreshes only the inactive registry. */
    suspend fun rollbackActivePackage(sourceId: SourceId): SourcePackageActivationState =
        operationMutex.withLock {
        try {
            val previous = platform.loadPreviousActivePackage(sourceId)
            val registry = platform.loadAvailableActiveRegistry(
                catalogCapabilities = catalogCapabilities,
                runtimeFactory = runtimeFactory,
                replacements = mapOf(sourceId to previous),
            )
            val activation = platform.rollbackActivePackage(sourceId)
            state.value = ExternalSourceRuntimeSnapshot(
                repository = platform.coordinator.snapshot.value,
                configuredRepositories = platform.coordinator.repositories(),
                registry = registry,
            )
            activation
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            state.value = state.value.copy(error = error)
            throw error
        }
        }

    /**
     * Returns installed packages whose repository manifest differs from the active artifact.
     * Version ordering is intentionally not inferred until the package version format is fixed.
     */
    suspend fun availablePackageUpdates(): List<SourcePackageUpdateCandidate> =
        operationMutex.withLock {
            platform.coordinator.availableSourceManifests().mapNotNull { manifest ->
                val active = try {
                    platform.loadActivePackage(manifest.sourceId)
                } catch (_: SourcePackageStateException) {
                    null
                } ?: return@mapNotNull null
                if (
                    active.manifest.packageVersion == manifest.packageVersion &&
                    active.manifest.sha256 == manifest.sha256
                ) {
                    null
                } else {
                    SourcePackageUpdateCandidate(
                        sourceId = manifest.sourceId,
                        installedVersion = active.installed.packageVersion,
                        installedSha256 = active.manifest.sha256,
                        availableManifest = manifest,
                    )
                }
            }
        }

    private fun rebuildActiveRegistry() {
        try {
            replaceRegistry(platform.coordinator.snapshot.value)
        } catch (error: Throwable) {
            state.value = state.value.copy(error = error)
            throw error
        }
    }

    private fun updateConfiguredRepositories() {
        state.value = state.value.copy(
            configuredRepositories = platform.coordinator.repositories(),
        )
    }

    private fun replaceRegistry(repository: SourceRepositoryLoadSnapshot) {
        val registry = platform.loadAvailableActiveRegistry(
            catalogCapabilities = catalogCapabilities,
            runtimeFactory = runtimeFactory,
        )
        state.value = ExternalSourceRuntimeSnapshot(
            repository = repository,
            configuredRepositories = platform.coordinator.repositories(),
            registry = registry,
        )
    }

    fun close() = platform.close()
}

data class ExternalSourceRuntimeSnapshot(
    val repository: SourceRepositoryLoadSnapshot,
    val configuredRepositories: List<SourceRepositoryEndpoint> = emptyList(),
    val registry: ExternalSourceRegistry? = null,
    val error: Throwable? = null,
)

data class SourcePackageUpdateCandidate(
    val sourceId: SourceId,
    val installedVersion: String,
    val installedSha256: String,
    val availableManifest: SourceManifest,
) {
    init {
        require(sourceId == availableManifest.sourceId) {
            "Update candidate source ID does not match its manifest"
        }
        require(installedVersion.isNotBlank()) { "Installed package version must not be blank" }
        require(installedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Installed package SHA-256 must be 64 lowercase hexadecimal characters"
        }
        require(
            installedVersion != availableManifest.packageVersion ||
                installedSha256 != availableManifest.sha256,
        ) {
            "Update candidate must contain a different package version or artifact"
        }
    }
}
