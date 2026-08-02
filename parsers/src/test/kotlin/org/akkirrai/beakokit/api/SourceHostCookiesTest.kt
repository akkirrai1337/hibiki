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
    fun `cookie limits and blank urls are rejected`() = runBlocking {
        val cookies = FakeCookies(requirements())

        assertFailsWith<IllegalArgumentException> { cookies.forUrl("") }
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
        capabilities = setOf(SourceHostCapability.COOKIES),
        networkPolicy = SourceHostNetworkPolicy(setOf("example.com")),
    )
}
