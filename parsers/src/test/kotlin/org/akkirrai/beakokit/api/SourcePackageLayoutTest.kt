package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SourcePackageLayoutTest {
    @Test
    fun `valid package layout is accepted`() {
        val manifest = manifest()
        SourcePackageLayoutValidator().requireValid(
            manifest,
            listOf(
                SourcePackageEntry("manifest.json", 100),
                SourcePackageEntry("source.wasm", 200),
                SourcePackageEntry("assets/", 0, directory = true),
                SourcePackageEntry("icon.png", 300),
            ),
        )
    }

    @Test
    fun `unsafe paths and symbolic links are rejected`() {
        val manifest = manifest()
        val violations = SourcePackageLayoutValidator().violations(
            manifest,
            listOf(
                SourcePackageEntry("manifest.json", 100),
                SourcePackageEntry("../outside.txt", 10),
                SourcePackageEntry("nested\\escape.txt", 10),
                SourcePackageEntry("link", 0, symbolicLink = true),
            ),
        )

        assertContains(violations, "Unsafe package entry path: ../outside.txt")
        assertContains(violations, "Unsafe package entry path: nested\\escape.txt")
        assertContains(violations, "Symbolic links are not allowed: link")
        assertContains(violations, "Package must contain the manifest entrypoint")
        assertFailsWith<SourcePackageLayoutException> {
            SourcePackageLayoutValidator().requireValid(
                manifest,
                listOf(SourcePackageEntry("manifest.json", 100)),
            )
        }
    }

    @Test
    fun `entry count and unpacked size limits are enforced`() {
        val manifest = manifest()
        val validator = SourcePackageLayoutValidator(maxEntryCount = 2, maxUnpackedSizeBytes = 100)
        val violations = validator.violations(
            manifest,
            listOf(
                SourcePackageEntry("manifest.json", 60),
                SourcePackageEntry("source.wasm", 60),
                SourcePackageEntry("extra.txt", 1),
            ),
        )

        assertContains(violations, "Package contains too many entries")
        assertContains(violations, "Package unpacked size exceeds the maximum allowed size")
    }

    @Test
    fun `file path cannot also be a parent of another entry`() {
        val violations = SourcePackageLayoutValidator().violations(
            manifest(),
            listOf(
                SourcePackageEntry("manifest.json", 100),
                SourcePackageEntry("source.wasm", 200),
                SourcePackageEntry("assets", 10),
                SourcePackageEntry("assets/icon.png", 20),
            ),
        )

        assertContains(violations, "Package file path conflicts with a child entry: assets")
    }

    @Test
    fun `manifest entrypoint cannot be a directory`() {
        val violations = SourcePackageLayoutValidator().violations(
            manifest(),
            listOf(
                SourcePackageEntry("manifest.json", 100),
                SourcePackageEntry("source.wasm/", 0, directory = true),
            ),
        )

        assertContains(violations, "Manifest entrypoint must be a file: source.wasm")
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime(id = "wasm", abi = "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1024,
        minClientVersion = 1,
    )
}
