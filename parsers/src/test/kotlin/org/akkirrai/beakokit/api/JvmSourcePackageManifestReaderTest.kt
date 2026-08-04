package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmSourcePackageManifestReaderTest {
    @Test
    fun `reader decodes manifest from package directory`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        val manifest = manifest()
        Files.writeString(
            packageDirectory.resolve("manifest.json"),
            Json.encodeToString(manifest),
        )

        assertEquals(manifest, JvmSourcePackageManifestReader().read(packageDirectory.toString()))
    }

    @Test
    fun `reader rejects a missing manifest`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageManifestReader().read(packageDirectory.toString())
        }
    }

    @Test
    fun `reader rejects an oversized manifest before decoding`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        Files.writeString(packageDirectory.resolve("manifest.json"), "{}")

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageManifestReader(maxManifestBytes = 1)
                .read(packageDirectory.toString())
        }
    }

    @Test
    fun `reader rejects malformed utf8`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        Files.write(packageDirectory.resolve("manifest.json"), byteArrayOf(0xC3.toByte()))

        assertFailsWith<SourcePackageStateException> {
            JvmSourcePackageManifestReader().read(packageDirectory.toString())
        }
    }

    @Test
    fun `reader rejects a symbolic link manifest`() {
        val packageDirectory = Files.createTempDirectory("hibiki-source-package-")
        val target = Files.createTempFile("hibiki-source-manifest-", ".json")
        Files.writeString(target, Json.encodeToString(manifest()))
        val link = runCatching {
            Files.createSymbolicLink(packageDirectory.resolve("manifest.json"), target)
        }.getOrNull() ?: return

        try {
            assertFailsWith<SourcePackageStateException> {
                JvmSourcePackageManifestReader().read(packageDirectory.toString())
            }
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(target)
        }
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "External source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1,
        minClientVersion = 1,
    )
}
