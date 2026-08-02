package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcePackageActivationStoreTest {
    @Test
    fun `failed activation does not persist state`() {
        val store = RecordingStore()
        val repository = repository(store)
        val old = packageVersion("1.0.0")
        store.state = SourcePackageActivationState(active = old)

        repository.activate(packageVersion("2.0.0"), initializationSucceeded = false)

        assertEquals(0, store.persistCount)
        assertEquals(SourcePackageActivationState(active = old), store.state)
    }

    @Test
    fun `successful activation and rollback persist atomically`() {
        val store = RecordingStore()
        val repository = repository(store)
        val old = packageVersion("1.0.0")
        val next = packageVersion("2.0.0")
        store.state = SourcePackageActivationState(active = old)

        repository.activate(next, initializationSucceeded = true)
        repository.rollback()

        assertEquals(2, store.persistCount)
        assertEquals(SourcePackageActivationState(active = old), store.state)
    }

    @Test
    fun `repository rejects packages belonging to another source`() {
        val repository = repository(RecordingStore())

        assertFailsWith<IllegalArgumentException> {
            repository.activate(
                InstalledSourcePackage(SourceId("another-source"), "1.0.0", "other"),
                initializationSucceeded = true,
            )
        }
    }

    @Test
    fun `first package can be deactivated after incomplete activation`() {
        val store = RecordingStore()
        val repository = repository(store)
        val first = packageVersion("1.0.0")
        store.state = SourcePackageActivationState(active = first)

        val state = repository.deactivateFirstPackage(first)

        assertEquals(SourcePackageActivationState(), state)
        assertEquals(SourcePackageActivationState(), store.state)
    }

    private fun repository(store: RecordingStore) = SourcePackageActivationRepository(
        sourceId = SourceId("external-source"),
        store = store,
    )

    private fun packageVersion(version: String) = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = version,
        packagePath = "packages/external-source/$version",
    )

    private class RecordingStore : SourcePackageActivationStore {
        var state = SourcePackageActivationState()
        var persistCount = 0

        override fun load(sourceId: SourceId): SourcePackageActivationState = state

        override fun persistAtomically(sourceId: SourceId, state: SourcePackageActivationState) {
            this.state = state
            persistCount++
        }
    }
}
