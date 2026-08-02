package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/** An already downloaded and validated package version. */
@Serializable
data class InstalledSourcePackage(
    val sourceId: SourceId,
    val packageVersion: String,
    val packagePath: String,
)

/** Persistable activation state; user configuration is intentionally outside this record. */
@Serializable
data class SourcePackageActivationState(
    val active: InstalledSourcePackage? = null,
    val previous: InstalledSourcePackage? = null,
)

/**
 * Pure activation policy used by platform storage adapters.
 *
 * The adapter persists the returned state atomically. If initialization fails, it must pass
 * `initializationSucceeded = false`, which leaves the old active package untouched.
 */
class SourcePackageActivationController(
    initialState: SourcePackageActivationState = SourcePackageActivationState(),
) {
    var state: SourcePackageActivationState = initialState
        private set

    fun activate(
        candidate: InstalledSourcePackage,
        initializationSucceeded: Boolean,
    ): SourcePackageActivationState {
        require(candidate.packageVersion.isNotBlank()) { "Package version must not be blank" }
        require(candidate.packagePath.isNotBlank()) { "Package path must not be blank" }
        if (!initializationSucceeded) return state

        state = state.copy(
            active = candidate,
            previous = state.active,
        )
        return state
    }

    fun rollback(): SourcePackageActivationState {
        val previous = state.previous ?: throw SourcePackageRollbackException(state.active?.sourceId)
        state = SourcePackageActivationState(
            active = previous,
            previous = null,
        )
        return state
    }
}

class SourcePackageRollbackException(
    val sourceId: SourceId?,
) : IllegalStateException("No previous source package version is available for rollback")
