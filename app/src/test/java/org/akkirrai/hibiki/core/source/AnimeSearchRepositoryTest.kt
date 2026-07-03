package org.akkirrai.hibiki.core.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSearchRepositoryTest {
    private fun repository(): AnimeSearchRepository {
        return AnimeSearchRepository(
            client = HttpClient(
                MockEngine { error("No network expected in AnimeSearchRepositoryTest") }
            )
        )
    }

    @Test
    fun `clearCaches clears both search and details caches`() {
        val repository = repository()

        repository.searchCache()["search:key"] = "value"
        repository.detailsCache()["details:key"] = "value"

        repository.clearCaches()

        assertTrue(repository.searchCache().isEmpty())
        assertTrue(repository.detailsCache().isEmpty())
    }

    @Test
    fun `repository instances do not share cache state`() {
        val first = repository()
        val second = repository()

        first.searchCache()["shared:key"] = "value"
        first.detailsCache()["shared:key"] = "value"

        assertFalse(first.searchCache().isEmpty())
        assertFalse(first.detailsCache().isEmpty())
        assertTrue(second.searchCache().isEmpty())
        assertTrue(second.detailsCache().isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeSearchRepository.searchCache(): MutableMap<String, Any> {
        val field = AnimeSearchRepository::class.java.getDeclaredField("searchCache")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun AnimeSearchRepository.detailsCache(): MutableMap<String, Any> {
        val field = AnimeSearchRepository::class.java.getDeclaredField("detailsCache")
        field.isAccessible = true
        return field.get(this) as MutableMap<String, Any>
    }
}
