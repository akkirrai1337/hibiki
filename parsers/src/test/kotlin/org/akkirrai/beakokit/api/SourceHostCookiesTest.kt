package org.akkirrai.beakokit.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHostCookiesTest {
    @Test
    fun `cookies require declared capability`() = runBlocking {
        val cookies = FakeCookies(SourceHostRequirements())

        assertFailsWith<SourceHostCapabilityException> {
            cookies.forUrl("https://example.com")
        }
    }

    @Test
    fun `cookies are stored and returned through host jar`() = runBlocking {
        val cookies = FakeCookies(requirements())

        cookies.storeFromResponse("https://example.com", mapOf("session" to "secret"))

        assertEquals(mapOf("session" to "secret"), cookies.forUrl("https://example.com"))
        cookies.clear("https://example.com")
        assertEquals(emptyMap(), cookies.forUrl("https://example.com"))
    }

    @Test
    fun `cookies reject origins outside the network policy`() = runBlocking {
        val cookies = FakeCookies(requirements())

        assertFailsWith<SourceHostNetworkPolicyException> {
            cookies.forUrl("https://other.example.com")
        }
    }

    @Test
    fun `cookie limits and blank urls are rejected`() = runBlocking {
        val cookies = FakeCookies(requirements())

        assertFailsWith<IllegalArgumentException> { cookies.forUrl("") }
        assertFailsWith<IllegalArgumentException> { cookies.forUrl("http://example.com") }
        assertFailsWith<IllegalArgumentException> { cookies.forUrl("file:///tmp/cookies") }
        assertFailsWith<IllegalArgumentException> { cookies.forUrl("https://") }
        assertFailsWith<IllegalArgumentException> { cookies.forUrl("https://example.com/path#fragment") }
        assertFailsWith<IllegalArgumentException> {
            cookies.storeFromResponse(
                "https://example.com",
                (0..SourceHostCookies.MAX_COOKIE_COUNT).associate { "cookie-$it" to "value" },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            cookies.storeFromResponse(
                "https://example.com",
                mapOf("session" to "x".repeat(SourceHostCookies.MAX_COOKIE_VALUE_LENGTH + 1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            cookies.storeFromResponse(
                "https://example.com",
                mapOf("session\nInjected" to "value"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            cookies.storeFromResponse(
                "https://example.com",
                mapOf("session=name" to "value"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            cookies.storeFromResponse(
                "https://example.com",
                mapOf("session" to "value\r\nInjected: true"),
            )
        }
    }

    @Test
    fun `host cookie reads reject oversized values`() = runBlocking {
        val cookies = object : SourceHostCookies() {
            override val requirements = requirements()

            override suspend fun cookiesForUrl(url: String): Map<String, String> =
                mapOf("session" to "x".repeat(SourceHostCookies.MAX_COOKIE_VALUE_LENGTH + 1))

            override suspend fun storeResponseCookies(url: String, cookies: Map<String, String>) = Unit

            override suspend fun clearCookies(url: String) = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            cookies.forUrl("https://example.com")
        }
    }

    private class FakeCookies(
        override val requirements: SourceHostRequirements,
    ) : SourceHostCookies() {
        private var values: Map<String, String> = emptyMap()

        protected override suspend fun cookiesForUrl(url: String): Map<String, String> = values

        protected override suspend fun storeResponseCookies(url: String, cookies: Map<String, String>) {
            values = cookies
        }

        protected override suspend fun clearCookies(url: String) {
            values = emptyMap()
        }
    }

    private fun requirements() = SourceHostRequirements(
        capabilities = setOf(SourceHostCapability.COOKIES, SourceHostCapability.NETWORK),
        networkPolicy = SourceHostNetworkPolicy(setOf("example.com")),
    )
}
