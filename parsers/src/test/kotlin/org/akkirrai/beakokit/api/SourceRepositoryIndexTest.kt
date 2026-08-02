package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRepositoryIndexTest {
    @Test
    fun `valid index exposes a package by stable source id`() {
        val index = SourceRepositoryIndex(
            apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
            sources = listOf(manifest()),
        )

        index.requireValid(clientVersion = 3)

        assertEquals(manifest(), index.find(SourceId("external-source")))
    }

    @Test
    fun `index rejects duplicate source ids and invalid packages`() {
        val invalidManifest = manifest(sha256 = "broken")
        val index = SourceRepositoryIndex(
            apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
            sources = listOf(invalidManifest, invalidManifest),
        )

        val violations = index.violations(clientVersion = 3)

        assertContains(violations, "Duplicate source ID in repository: external-source")
        assertContains(violations, "external-source: SHA-256 must be 64 lowercase hexadecimal characters")
        assertFailsWith<SourceRepositoryIndexException> {
            index.requireValid(clientVersion = 3)
        }
    }

    @Test
    fun `index requires its own api version and source metadata`() {
        val index = SourceRepositoryIndex(
            apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION + 1,
            sources = listOf(manifest().copy(sourceInfo = null)),
        )

        val violations = index.violations(clientVersion = 3)

        assertContains(violations, "Unsupported source repository API version: 2")
        assertContains(violations, "Source metadata is required for repository source: external-source")
    }

    private fun manifest(sha256: String = "a".repeat(64)) = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "External source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime(id = "wasm", abi = "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = sha256,
        artifactSizeBytes = 1024,
        minClientVersion = 1,
    )
}
