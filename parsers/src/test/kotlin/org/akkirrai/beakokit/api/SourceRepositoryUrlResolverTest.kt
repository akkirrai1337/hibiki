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
    fun `resolves GitHub tree link with an explicit ref to raw index URL`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/repository/index.json",
            resolver.resolve(
                "https://github.com/vadim/hibiki-sources/tree/main/repository/index.json",
            ).url,
        )
    }

    @Test
    fun `resolves GitHub tree directory to its conventional index file`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/repository/index.json",
            resolver.resolve(
                "https://github.com/vadim/hibiki-sources/tree/main/repository",
            ).url,
        )
    }

    @Test
    fun `preserves raw GitHub link`() {
        val url = "https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json"

        assertEquals(url, resolver.resolve(url).url)
    }

    @Test
    fun `rejects unsafe raw GitHub links`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://raw.githubusercontent.com/vadim/hibiki-sources/main/../index.json")
        }
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json?raw=1")
        }
    }

    @Test
    fun `accepts GitHub host casing variations`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/index.json",
            resolver.resolve("https://GITHUB.com/vadim/hibiki-sources/blob/main/index.json").url,
        )
    }

    @Test
    fun `resolves GitHub repository root to conventional main index URL`() {
        assertEquals(
            "https://raw.githubusercontent.com/vadim/hibiki-sources/main/repository/index.json",
            resolver.resolve("https://github.com/vadim/hibiki-sources").url,
        )
    }

    @Test
    fun `rejects GitHub links with query or fragment`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources/blob/main/index.json?raw=1")
        }
    }

    @Test
    fun `rejects GitHub links with unsafe path segments`() {
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources/blob/main/../index.json")
        }
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources/blob/main/nested\\index.json")
        }
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://github.com/vadim/hibiki-sources/blob/main/%2e%2e/index.json")
        }
        assertFailsWith<SourceRepositoryUrlException> {
            resolver.resolve("https://raw.githubusercontent.com/vadim/hibiki-sources/main/%2findex.json")
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

    @Test
    fun `rejects direct repository fragments`() {
        assertFailsWith<IllegalArgumentException> {
            SourceRepositoryEndpoint("https://example.test/index.json#fragment")
        }
    }

    @Test
    fun `rejects repository links containing line breaks before trimming`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.resolve("\nhttps://example.test/index.json")
        }
    }
}
