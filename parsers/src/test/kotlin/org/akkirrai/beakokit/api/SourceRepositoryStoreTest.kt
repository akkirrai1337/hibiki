package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRepositoryStoreTest {
    @Test
    fun endpointRequiresHttps() {
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryEndpoint("http://example.test/index.json")
        }
    }

    @Test
    fun endpointRejectsMalformedHttpsUrls() {
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryEndpoint("https://")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryEndpoint("https-not-a-url")
        }
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryEndpoint("https://user:password@example.test/index.json")
        }
    }

    @Test
    fun addDeduplicatesWithoutPersistingAgain() {
        val store = FakeStore(
            mutableListOf(SourceRepositoryEndpoint("https://one.test/index.json")),
        )
        val catalog = SourceRepositoryCatalog(store)
        val endpoint = SourceRepositoryEndpoint("https://one.test/index.json")

        assertEquals(listOf(endpoint), catalog.add(endpoint))
        assertEquals(0, store.persistCalls)
    }

    @Test
    fun addAndRemovePersistTheUpdatedListAtomically() {
        val store = FakeStore()
        val catalog = SourceRepositoryCatalog(store)
        val first = SourceRepositoryEndpoint("https://one.test/index.json")
        val second = SourceRepositoryEndpoint("https://two.test/index.json")

        assertEquals(listOf(first, second), catalog.add(first).let { catalog.add(second) })
        assertEquals(listOf(second), catalog.remove(first.url))
        assertEquals(3, store.persistCalls)
    }

    @Test
    fun loadRejectsDuplicatePersistedUrls() {
        val endpoint = SourceRepositoryEndpoint("https://one.test/index.json")
        val store = FakeStore(mutableListOf(endpoint, endpoint))

        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryCatalog(store).load()
        }
    }

    private class FakeStore(
        private var repositories: MutableList<SourceRepositoryEndpoint> = mutableListOf(),
    ) : SourceRepositoryStore {
        var persistCalls: Int = 0
            private set

        override fun load(): List<SourceRepositoryEndpoint> = repositories.toList()

        override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
            persistCalls += 1
            this.repositories = repositories.toMutableList()
        }
    }
}
