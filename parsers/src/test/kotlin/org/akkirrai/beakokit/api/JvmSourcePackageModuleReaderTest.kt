package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class JvmSourcePackageModuleReaderTest {
    @Test
    fun `reader loads the declared entrypoint`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        val bytes = byteArrayOf(0, 1, 2, 3)
        Files.write(packageDirectory.resolve("source.wasm"), bytes)

        assertContentEquals(bytes, JvmSourcePackageModuleReader().read(packageDirectory.toString(), "source.wasm"))
    }

    @Test
    fun `reader rejects unsafe entrypoints`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")

        assertFailsWith<IllegalArgumentException> {
            JvmSourcePackageModuleReader().read(packageDirectory.toString(), "../source.wasm")
        }
    }

    @Test
    fun `reader rejects the package manifest as a runtime module`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        Files.writeString(packageDirectory.resolve("manifest.json"), "{}")

        assertFailsWith<IllegalArgumentException> {
            JvmSourcePackageModuleReader().read(packageDirectory.toString(), "manifest.json")
        }
    }

    @Test
    fun `reader rejects modules over the configured limit`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        Files.write(packageDirectory.resolve("source.wasm"), byteArrayOf(0, 1, 2))

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageModuleReader(maxModuleBytes = 2)
                .read(packageDirectory.toString(), "source.wasm")
        }
    }

    @Test
    fun `reader rejects a limit that cannot fit in a byte array`() {
        assertFailsWith<IllegalArgumentException> {
            JvmSourcePackageModuleReader(maxModuleBytes = Long.MAX_VALUE)
        }
    }

    @Test
    fun `reader rejects a symbolic link entrypoint`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        val target = Files.createTempFile("hibiki-source-module-", ".wasm")
        Files.write(target, byteArrayOf(0, 1, 2))
        val link = runCatching {
            Files.createSymbolicLink(packageDirectory.resolve("source.wasm"), target)
        }.getOrNull() ?: return

        try {
            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageModuleReader().read(packageDirectory.toString(), "source.wasm")
            }
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(target)
        }
    }

    @Test
    fun `reader rejects a symbolic link directory in the entrypoint path`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        val targetDirectory = Files.createTempDirectory("hibiki-source-module-dir-")
        Files.write(targetDirectory.resolve("source.wasm"), byteArrayOf(0, 1, 2))
        val linkDirectory = runCatching {
            Files.createSymbolicLink(packageDirectory.resolve("modules"), targetDirectory)
        }.getOrNull() ?: return

        try {
            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageModuleReader().read(packageDirectory.toString(), "modules/source.wasm")
            }
        } finally {
            Files.deleteIfExists(linkDirectory)
            Files.deleteIfExists(targetDirectory.resolve("source.wasm"))
            Files.deleteIfExists(targetDirectory)
        }
    }

    @Test
    fun `reader rejects a symbolic link package directory`() {
        val targetDirectory = Files.createTempDirectory("hibiki-source-package-target-")
        Files.write(targetDirectory.resolve("source.wasm"), byteArrayOf(0, 1, 2))
        val linkDirectory = runCatching {
            Files.createSymbolicLink(
                targetDirectory.parent.resolve("hibiki-source-package-link-${System.nanoTime()}"),
                targetDirectory,
            )
        }.getOrNull() ?: return

        try {
            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageModuleReader().read(linkDirectory.toString(), "source.wasm")
            }
        } finally {
            Files.deleteIfExists(linkDirectory)
            Files.deleteIfExists(targetDirectory.resolve("source.wasm"))
            Files.deleteIfExists(targetDirectory)
        }
    }
}
