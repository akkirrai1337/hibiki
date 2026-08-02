package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun packageVersion(version: String) = InstalledSourcePackage(
        sourceId = SourceId("external-source"),
        packageVersion = version,
        packagePath = "packages/external-source/$version",
    )
}
