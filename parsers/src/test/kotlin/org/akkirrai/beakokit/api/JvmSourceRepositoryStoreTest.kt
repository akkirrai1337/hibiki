package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Files

class JvmSourceRepositoryStoreTest {
    @Test
    fun persistsAndLoadsEndpoints() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        val endpoints = listOf(
            SourceRepositoryEndpoint("https://one.test/index.json"),
            SourceRepositoryEndpoint("https://two.test/index.json"),
        )

        JvmSourceRepositoryStore(file).persistAtomically(endpoints)

        assertEquals(endpoints, JvmSourceRepositoryStore(file).load())
    }

    @Test
    fun restoresFromBackupWhenPrimaryIsCorrupted() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        val store = JvmSourceRepositoryStore(file)
        val endpoint = SourceRepositoryEndpoint("https://one.test/index.json")
        store.persistAtomically(listOf(endpoint))
        store.persistAtomically(listOf(SourceRepositoryEndpoint("https://two.test/index.json")))
        Files.writeString(file, "not-json")

        assertEquals(listOf(endpoint), store.load())
        assertTrue(Files.readString(file).contains(endpoint.url))
    }

    @Test
    fun rejectsDuplicateEndpoints() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        val endpoint = SourceRepositoryEndpoint("https://one.test/index.json")

        assertFailsWith<IllegalArgumentException> {
            JvmSourceRepositoryStore(file).persistAtomically(listOf(endpoint, endpoint))
        }
    }
}
