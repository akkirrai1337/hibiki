package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmSourcePackageActivationStoreTest {
    @Test
    fun `file store persists and restores activation state`() {
        val root = Files.createTempDirectory("hibiki-source-state-")
        try {
            val sourceId = SourceId("external-source")
            val state = SourcePackageActivationState(
                active = packageVersion("2.0.0"),
                previous = packageVersion("1.0.0"),
            )
            val store = JvmSourcePackageActivationStore(root)

            store.persistAtomically(sourceId, state)

            assertEquals(state, store.load(sourceId))
            assertEquals(listOf("external-source.json"), Files.list(root).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            })
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `missing source state starts empty`() {
        val root = Files.createTempDirectory("hibiki-source-state-")
        try {
            assertEquals(
                SourcePackageActivationState(),
                JvmSourcePackageActivationStore(root).load(SourceId("missing-source")),
            )
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `backup restores state after interrupted replacement`() {
        val root = Files.createTempDirectory("hibiki-source-state-")
        try {
            val sourceId = SourceId("external-source")
            val old = SourcePackageActivationState(active = packageVersion("1.0.0"))
            val next = SourcePackageActivationState(active = packageVersion("2.0.0"))
            val store = JvmSourcePackageActivationStore(root)
            store.persistAtomically(sourceId, old)
            store.persistAtomically(sourceId, next)
            Files.writeString(root.resolve("external-source.json"), "broken")

            assertEquals(old, store.load(sourceId))
        } finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `symbolic link state is rejected`() {
        val root = Files.createTempDirectory("hibiki-source-state-")
        val target = Files.createTempFile("hibiki-source-state-", ".json")
        Files.writeString(target, "{}")
        val link = runCatching {
            Files.createSymbolicLink(root.resolve("external-source.json"), target)
        }.getOrNull() ?: return

        try {
            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageActivationStore(root).load(SourceId("external-source"))
            }
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(target)
        }
    }

    @Test
    fun `oversized state is rejected before decoding`() {
        val root = Files.createTempDirectory("hibiki-source-state-")
        val file = root.resolve("external-source.json")
        Files.writeString(file, "{}")

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageActivationStore(root, maxStateBytes = 1)
                .load(SourceId("external-source"))
        }
    }

    private fun packageVersion(version: String) = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = version,
        packagePath = "packages/external-source/$version",
    )
}
