package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryDisplayNameTest {
    @Test
    fun `github index urls open the repository page`() {
        assertEquals(
            "https://github.com/akkirrai1337/hibiki-sources",
            repositoryBrowseUrl("https://github.com/akkirrai1337/hibiki-sources/blob/main/repository/index.json"),
        )
        assertEquals(
            "https://github.com/akkirrai1337/hibiki-sources",
            repositoryBrowseUrl("https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/repository/index.json"),
        )
    }

    @Test
    fun `non github index urls are left unchanged`() {
        val url = "https://example.test/repository/index.json"
        assertEquals(url, repositoryBrowseUrl(url))
    }

    @Test
    fun `github repository url uses repository segment`() {
        assertEquals(
            "akkirrai1337/hibiki-sources",
            repositoryDisplayName("https://github.com/akkirrai1337/hibiki-sources/blob/main/index.json"),
        )
    }

    @Test
    fun `raw github repository url uses repository segment`() {
        assertEquals(
            "akkirrai1337/hibiki-sources",
            repositoryDisplayName("https://raw.githubusercontent.com/akkirrai1337/hibiki-sources/main/index.json"),
        )
    }

    @Test
    fun `repository suffixes and query are omitted`() {
        assertEquals(
            "sources",
            repositoryDisplayName("https://example.test/sources.git/index.json?raw=1"),
        )
    }

    @Test
    fun `unknown host uses last meaningful path segment`() {
        assertEquals(
            "sources",
            repositoryDisplayName("https://downloads.example.test/team/sources/index.json"),
        )
    }

    @Test
    fun `host is used when url has no path`() {
        assertEquals(
            "example.test",
            repositoryDisplayName("https://example.test"),
        )
    }
}
