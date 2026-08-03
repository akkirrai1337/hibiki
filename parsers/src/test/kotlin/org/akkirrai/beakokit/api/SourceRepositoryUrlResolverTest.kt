package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRepositoryUrlResolverTest {
    private val resolver = SourceRepositoryUrlResolver()

    @Test
    fun `resolves GitHub blob link to raw index URL`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/repository/index.json",
            resolver.resolve(
                " https://github.com/vadim/hibiki-sources/blob/main/repository/index.json ",
            ).url,
        )
    }

    @Test
    fun `preserves raw GitHub link`() {
        val url = "https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json"

        assertEquals(url, resolver.resolve(url).url)
    }

    @Test
    fun `accepts GitHub host casing variations`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json",
            resolver.resolve("https://GITHUB.com/vadim/hibiki-sources/blob/main/index.json").url,
        )
    }

    @Test
    fun `rejects GitHub repository root without an index path`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources")
        }
    }

    @Test
    fun `rejects GitHub links with query or fragment`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources/blob/main/index.json?raw=1")
        }
    }

    @Test
    fun `classifies invalid direct URL as a repository URL error`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("not-https")
        }
    }

    @Test
    fun `rejects direct http links before they reach repository storage`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("http://example.test/index.json")
        }
    }
}
