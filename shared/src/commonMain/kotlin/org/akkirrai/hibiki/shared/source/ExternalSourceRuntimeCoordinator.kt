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
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryLoadSnapshot
import org.akkirrai.beakokit.model.CatalogCapabilities

/** Background owner of repository refreshes and the inactive external registry. */
class ExternalSourceRuntimeCoordinator(
    private val platform: ExternalSourceRepositoryPlatform,
    private val catalogCapabilities: (org.akkirrai.beakokit.api.SourceManifest) -> CatalogCapabilities,
    private val runtimeFactory: ExternalSourceRuntimeFactory,
    private val sourceContextFactory: ((SourceId) -> SourceContext)? = null,
) : ExternalSourceRepositoryActions {
    private val operationMutex = Mutex()
    private val state = MutableStateFlow(
        ExternalSourceRuntimeSnapshot(
            repository = platform.coordinator.snapshot.value,
            configuredRepositories = platform.coordinator.repositories(),
        ),
    )

    val snapshot: StateFlow<ExternalSourceRuntimeSnapshot> = state.asStateFlow()

    /** Returns the persisted repository endpoints without loading their indexes. */
    override suspend fun repositories(): List<SourceRepositoryEndpoint> = operationMutex.withLock {
        platform.coordinator.repositories()
    }

    /** Returns the active package for a source, or null when it has not been installed. */
    suspend fun activePackage(sourceId: SourceId): ActiveExternalSourcePackage? =
        operationMutex.withLock {
            loadActivePackageOrNull(sourceId)
        }

    /** Returns advertised packages together with their currently active versions. */
    suspend fun packageStatuses(): List<ExternalSourcePackageStatus> = operationMutex.withLock {
        packageStatusesLocked()
    }

    /** Refreshes repositories and replaces only the inactive external registry on success. */
    suspend fun refresh() = operationMutex.withLock {
        refreshLocked()
    }

    /** Adds one repository and refreshes only the inactive external registry. */
    suspend fun addRepository(endpoint: SourceRepositoryEndpoint) = operationMutex.withLock {
        val previous = platform.coordinator.repositories()
        platform.coordinator.addRepository(endpoint)
        if (platform.coordinator.repositories() == previous) {
            return@withLock state.value.repository
        }
        updateConfiguredRepositories()
        refreshLocked()
    }

    /** Removes one repository and refreshes only the inactive external registry. */
    suspend fun removeRepository(url: String) = operationMutex.withLock {
        val previous = platform.coordinator.repositories()
        platform.coordinator.removeRepository(url)
        if (platform.coordinator.repositories() == previous) {
            return@withLock state.value.repository
        }
        updateConfiguredRepositories()
        refreshLocked()
    }

    override suspend fun refreshRepositories() {
        refresh()
    }

    override suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus> =
        packageStatuses()

    override suspend fun installAvailablePackageFromUi(
        sourceId: SourceId,
        initialize: suspend () -> Unit,
    ) {
        installAvailablePackage(sourceId, initialize = initialize)
    }

    override suspend fun rollbackPackageFromUi(sourceId: SourceId) {
        rollbackActivePackage(sourceId)
    }

    override suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint) {
        addRepository(endpoint)
    }

    override suspend fun removeRepositoryFromUi(url: String) {
        removeRepository(url)
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
            activation = platform.installAvailablePackage(
                sourceId = sourceId,
                initialize = initialize,
                initializeCandidate = sourceContextFactory?.let { contextFactory ->
                    { candidate ->
                        val manifest = platform.coordinator.availableSourceManifest(sourceId)
                            ?: throw SourcePackageStateException(
                                "Source package is no longer advertised by a loaded repository: $sourceId",
                            )
                        runtimeFactory.create(
                            ActiveExternalSourcePackage(manifest = manifest, installed = candidate),
                            contextFactory(sourceId),
                        )
                    }
                },
            )
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
            packageStatusesLocked().mapNotNull { status ->
                val active = status.activePackage ?: return@mapNotNull null
                if (!status.updateAvailable) return@mapNotNull null
                SourcePackageUpdateCandidate(
                    sourceId = status.sourceId,
                    installedVersion = active.installed.packageVersion,
                    installedSha256 = active.manifest.sha256,
                    availableManifest = status.availableManifest,
                )
            }
        }

    private fun packageStatusesLocked(): List<ExternalSourcePackageStatus> =
        platform.coordinator.availableSourceManifests().map { manifest ->
            val active = loadActivePackageOrNull(manifest.sourceId)
            ExternalSourcePackageStatus(
                sourceId = manifest.sourceId,
                availableManifest = manifest,
                activePackage = active,
                rollbackAvailable = active != null && runCatching {
                    platform.loadPreviousActivePackage(manifest.sourceId)
                }.isSuccess,
            )
        }

    private fun loadActivePackageOrNull(sourceId: SourceId): ActiveExternalSourcePackage? = try {
        platform.loadActivePackage(sourceId)
    } catch (_: SourcePackageStateException) {
        null
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

/** Narrow repository-management boundary for shared settings UI. */
interface ExternalSourceRepositoryActions {
    suspend fun repositories(): List<SourceRepositoryEndpoint>

    suspend fun addRepositoryFromUi(endpoint: SourceRepositoryEndpoint)

    suspend fun removeRepositoryFromUi(url: String)

    suspend fun refreshRepositories()

    suspend fun packageStatusesForUi(): List<ExternalSourcePackageStatus>

    /** The host supplies initialization because it owns the platform runtime setup. */
    suspend fun installAvailablePackageFromUi(
        sourceId: SourceId,
        initialize: suspend () -> Unit,
    )

    suspend fun rollbackPackageFromUi(sourceId: SourceId)
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

data class ExternalSourcePackageStatus(
    val sourceId: SourceId,
    val availableManifest: SourceManifest,
    val activePackage: ActiveExternalSourcePackage?,
    val rollbackAvailable: Boolean = false,
) {
    init {
        require(sourceId == availableManifest.sourceId) {
            "Package status source ID does not match its manifest"
        }
    }

    val updateAvailable: Boolean
        get() = activePackage != null && (
            activePackage.manifest.packageVersion != availableManifest.packageVersion ||
                activePackage.manifest.sha256 != availableManifest.sha256
            )
}
