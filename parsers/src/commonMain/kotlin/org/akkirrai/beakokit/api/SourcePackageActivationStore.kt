package org.akkirrai.beakokit.api

/** Platform storage boundary for one source's activation state. */
interface SourcePackageActivationStore {
    fun load(sourceId: SourceId): SourcePackageActivationState

    /** Implementations must replace the persisted state atomically. */
    fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState)
}

/** Coordinates activation policy with a platform-provided atomic state store. */
class SourcePackageActivationRepository(
    private val sourceId: SourceId,
    private val store: SourcePackageActivationStore,
) {
    fun load(): SourcePackageActivationState = checkedState(store.load(sourceId))

    fun activate(
        candidate: InstalledSourcePackage,
        initializationSucceeded: Boolean,
    ): SourcePackageActivationState {
        require(candidate.sourceId == sourceId) {
            "Package source ID does not match activation repository: ${candidate.sourceId}"
        }
        val current = load()
        val controller = SourcePackageActivationController(current)
        val next = controller.activate(candidate, initializationSucceeded)
        if (next != current) store.persistAtomically(sourceId, next)
        return next
    }

    fun rollback(): SourcePackageActivationState {
        val current = load()
        val next = SourcePackageActivationController(current).rollback()
        store.persistAtomically(sourceId, next)
        return next
    }

    private fun checkedState(state: SourcePackageActivationState): SourcePackageActivationState {
        require(state.active?.sourceId == null || state.active.sourceId == sourceId) {
            "Active package source ID does not match activation repository"
        }
        require(state.previous?.sourceId == null || state.previous.sourceId == sourceId) {
            "Previous package source ID does not match activation repository"
        }
        return state
    }
}

