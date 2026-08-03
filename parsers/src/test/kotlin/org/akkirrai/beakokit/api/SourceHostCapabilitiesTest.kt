package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceHostCapabilitiesTest {
    @Test
    fun `requirements expose only declared capabilities`() {
        val requirements = SourceHostRequirements(
            setOf(SourceHostCapability.NETWORK, SourceHostCapability.LOGGING),
        )

        assertTrue(requirements.requires(SourceHostCapability.NETWORK))
        assertFalse(requirements.requires(SourceHostCapability.STORAGE))
    }

    @Test
    fun `manifest requirements are copied before entering the runtime`() {
        val capabilities = mutableSetOf(SourceHostCapability.NETWORK)
        val hosts = mutableSetOf("example.com")
        val manifest = SourceManifest(
            manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
            sourceId = SourceId("external-source"),
            packageVersion = "1.0.0",
            apiVersion = SourceApi.VERSION,
            runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
            entrypoint = "source.wasm",
            packageUrl = "https://example.com/source.zip",
            sha256 = "a".repeat(64),
            artifactSizeBytes = 1,
            minClientVersion = 0,
            hostCapabilities = capabilities,
            hostNetworkPolicy = SourceHostNetworkPolicy(hosts),
        )

        val requirements = manifest.hostRequirements()
        capabilities.clear()
        hosts.clear()

        assertTrue(requirements.requires(SourceHostCapability.NETWORK))
        assertTrue(requirements.networkPolicy.allows("https://example.com/path"))
    }

    @Test
    fun `host access rejects undeclared capabilities`() {
        val access = TestHostAccess(
            SourceHostRequirements(setOf(SourceHostCapability.NETWORK)),
        )

        access.require(SourceHostCapability.NETWORK)
        assertFailsWith<SourceHostCapabilityException> {
            access.require(SourceHostCapability.COOKIES)
        }
    }

    @Test
    fun `manifest host permissions preserve declared network origins`() {
        val manifest = SourceManifest(
            manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
            sourceId = SourceId("external-source"),
            packageVersion = "1.0.0",
            apiVersion = SourceApi.VERSION,
            runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
            entrypoint = "source.wasm",
            packageUrl = "https://example.com/source.zip",
            sha256 = "a".repeat(64),
            artifactSizeBytes = 1024,
            minClientVersion = 1,
            hostCapabilities = setOf(SourceHostCapability.NETWORK),
            hostNetworkPolicy = SourceHostNetworkPolicy(setOf("api.example.com")),
        )

        val requirements = manifest.hostRequirements()

        assertTrue(requirements.requires(SourceHostCapability.NETWORK))
        assertTrue(requirements.networkPolicy.allows("https://api.example.com/catalog"))
        assertFalse(requirements.networkPolicy.allows("https://other.example.com/catalog"))
    }

    @Test
    fun `network policy treats host names as case insensitive`() {
        val policy = SourceHostNetworkPolicy(setOf("api.example.com"))

        assertTrue(policy.allows("https://API.EXAMPLE.COM/catalog"))
    }

    private data class TestHostAccess(
        override val requirements: SourceHostRequirements,
    ) : SourceHostAccess
}
