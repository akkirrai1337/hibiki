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
    fun `reader rejects modules over the configured limit`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        Files.write(packageDirectory.resolve("source.wasm"), byteArrayOf(0, 1, 2))

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageModuleReader(maxModuleBytes = 2)
                .read(packageDirectory.toString(), "source.wasm")
        }
    }
}
