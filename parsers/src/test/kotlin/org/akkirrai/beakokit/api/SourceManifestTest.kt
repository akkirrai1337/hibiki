package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SourceManifestTest {
    @Test
    fun `valid manifest passes package compatibility checks`() {
        manifest().requireValid(clientVersion = 3, supportedApiVersion = SourceApi.VERSION)
    }

    @Test
    fun `manifest rejects invalid checksum and incompatible client`() {
        val invalid = manifest(sha256 = "broken", minClientVersion = 4)

        val violations = invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION)

        assertContains(violations, "SHA-256 must be 64 lowercase hexadecimal characters")
        assertContains(violations, "Client version is outside the package compatibility range")
        assertFailsWith<SourceManifestException> {
            invalid.requireValid(clientVersion = 3, supportedApiVersion = SourceApi.VERSION)
        }
    }

    @Test
    fun `manifest rejects unsupported api and format versions`() {
        val invalid = manifest(manifestFormatVersion = 2, apiVersion = SourceApi.VERSION + 1)

        val violations = invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION)

        assertContains(violations, "Unsupported manifest format version: 2")
        assertContains(violations, "Unsupported source API version: ${SourceApi.VERSION + 1}")
    }

    @Test
    fun `manifest rejects unsupported host API version`() {
        val invalid = manifest(hostApiVersion = SourceHostApi.VERSION + 1)

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Unsupported source host API version: ${SourceHostApi.VERSION + 1}",
        )
    }

    @Test
    fun `manifest rejects malformed package url`() {
        val invalid = manifest().copy(packageUrl = "https://")

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Package URL must be a valid HTTPS URL",
        )
    }

    @Test
    fun `manifest rejects unsafe package entrypoint before download`() {
        val invalid = manifest().copy(entrypoint = "../source.wasm")

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Entrypoint must be a safe relative package path",
        )
    }

    @Test
    fun `manifest rejects manifest json as runtime entrypoint`() {
        val invalid = manifest().copy(entrypoint = "manifest.json")

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Entrypoint must not point to manifest.json",
        )
    }

    @Test
    fun `package compatibility ignores archive metadata but not source metadata`() {
        val repositoryManifest = manifest()
        val packageManifest = repositoryManifest.copy(
            sha256 = "b".repeat(64),
            artifactSizeBytes = 2048,
        )

        assertTrue(repositoryManifest.matchesPackageManifest(packageManifest))
        assertFalse(
            repositoryManifest.matchesPackageManifest(
                packageManifest.copy(runtime = SourceRuntime("other-runtime", "abi-1")),
            ),
        )
    }

    @Test
    fun `network policy requires network capability`() {
        val invalid = manifest().copy(
            hostNetworkPolicy = SourceHostNetworkPolicy(setOf("example.com")),
        )

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Network policy requires the NETWORK host capability",
        )
    }

    @Test
    fun `config schema requires config host capability`() {
        val invalid = manifest().copy(
            sourceInfo = SourceManifestInfo(
                displayName = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                configSchema = SourceConfigSchema(
                    listOf(SourceConfigField("api_token", SourceConfigValueKind.SECRET)),
                ),
            ),
        )

        assertContains(
            invalid.violations(clientVersion = 3, supportedApiVersion = SourceApi.VERSION),
            "Source config schema requires the CONFIG host capability",
        )
    }

    @Test
    fun `manifest round trip preserves external config schema`() {
        val sourceManifest = manifest().copy(
            sourceInfo = SourceManifestInfo(
                displayName = "External source",
                languages = setOf(SourceLanguage.ENGLISH),
                primaryLanguage = SourceLanguage.ENGLISH,
                configSchema = SourceConfigSchema(
                    listOf(
                        SourceConfigField(
                            key = "base_url",
                            kind = SourceConfigValueKind.HTTPS_URL,
                            required = true,
                        ),
                        SourceConfigField(
                            key = "api_token",
                            kind = SourceConfigValueKind.SECRET,
                        ),
                    ),
                ),
            ),
        )

        val decoded = Json.decodeFromString<SourceManifest>(
            Json.encodeToString(sourceManifest),
        )

        assertEquals(sourceManifest, decoded)
    }

    private fun manifest(
        manifestFormatVersion: Int = SourceManifest.CURRENT_FORMAT_VERSION,
        apiVersion: Int = SourceApi.VERSION,
        hostApiVersion: Int = SourceHostApi.VERSION,
        sha256: String = "a".repeat(64),
        minClientVersion: Int = 1,
    ) = SourceManifest(
        manifestFormatVersion = manifestFormatVersion,
        sourceId = SourceId("external-source"),
        packageVersion = "1.0.0",
        apiVersion = apiVersion,
        hostApiVersion = hostApiVersion,
        runtime = SourceRuntime(id = "wasm", abi = "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = sha256,
        artifactSizeBytes = 1024,
        minClientVersion = minClientVersion,
    )
}
