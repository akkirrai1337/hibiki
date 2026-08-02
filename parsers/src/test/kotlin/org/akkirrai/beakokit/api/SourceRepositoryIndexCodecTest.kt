package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRepositoryIndexCodecTest {
    @Test
    fun `repository index round trips through json`() {
        val index = SourceRepositoryIndex(
            apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
            sources = listOf(manifest()),
        )

        val restored = SourceRepositoryIndexCodec.decode(
            SourceRepositoryIndexCodec.encode(index),
        )

        assertEquals(index, restored)
    }

    @Test
    fun `decode and validate rejects an incompatible package`() {
        val value = SourceRepositoryIndexCodec.encode(
            SourceRepositoryIndex(
                apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
                sources = listOf(manifest(sha256 = "broken")),
            ),
        )

        val error = assertFailsWith<SourceRepositoryIndexException> {
            SourceRepositoryIndexCodec.decodeAndValidate(value, clientVersion = 3)
        }

        assertContains(
            error.violations,
            "external-source: SHA-256 must be 64 lowercase hexadecimal characters",
        )
    }

    @Test
    fun `decode rejects unknown fields before validation`() {
        val value = SourceRepositoryIndexCodec.encode(
            SourceRepositoryIndex(
                apiVersion = SourceRepositoryIndex.CURRENT_API_VERSION,
                sources = listOf(manifest()),
            ),
        ).replaceFirst("{", "{\"unexpected\":true,")

        val error = assertFailsWith<SourceRepositoryIndexException> {
            SourceRepositoryIndexCodec.decode(value)
        }

        assertContains(error.violations.single(), "Repository index JSON is invalid")
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
