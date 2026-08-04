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

    @Test
    fun rejectsSymbolicLinkState() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        val target = Files.createTempFile("beakokit-repositories-", ".json")
        Files.writeString(target, "[]")
        val link = runCatching { Files.createSymbolicLink(file, target) }.getOrNull() ?: return

        try {
            assertFailsWith<SourceRepositoryStateException> {
                JvmSourceRepositoryStore(file).load()
            }
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(target)
        }
    }

    @Test
    fun rejectsOversizedStateBeforeDecoding() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        Files.writeString(file, "[]")

        assertFailsWith<SourceRepositoryStateException> {
            JvmSourceRepositoryStore(file, maxRepositoryBytes = 1).load()
        }
    }

    @Test
    fun rejectsOversizedStateBeforeWriting() {
        val directory = Files.createTempDirectory("beakokit-repositories")
        val file = directory.resolve("repositories.json")
        val endpoint = SourceRepositoryEndpoint("https://one.test/index.json")

        assertFailsWith<IllegalArgumentException> {
            JvmSourceRepositoryStore(file, maxRepositoryBytes = 1).persistAtomically(listOf(endpoint))
        }
        assertTrue(!Files.exists(file))
    }
}
