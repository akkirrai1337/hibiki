package org.akkirrai.beakokit.api

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceResultCacheTest {
    private val sourceId = SourceId("cache-test")

    @Test
    fun `cache reuses a safe operation result until its TTL expires`() = runBlocking {
        var now = 0L
        var loads = 0
        val cache = SourceResultCache(nowMillis = { now })

        suspend fun load() = cache.getOrLoad(sourceId, SourceOperation.DETAILS, "42", 100) {
            ++loads
        }

        assertEquals(1, load())
        assertEquals(1, load())
        now = 100
        assertEquals(2, load())
        assertEquals(2, loads)
    }

    @Test
    fun `single flight shares one concurrent load`() = runBlocking {
        var loads = 0
        val cache = SourceResultCache()

        val results = List(5) {
            async {
                cache.getOrLoad(sourceId, SourceOperation.SEARCH, "naruto", 1_000) {
                    loads += 1
                    delay(10)
                    "result"
                }
            }
        }.awaitAll()

        assertEquals(List(5) { "result" }, results)
        assertEquals(1, loads)
    }

    @Test
    fun `failed result is not cached`() = runBlocking {
        var loads = 0
        val cache = SourceResultCache()

        repeat(2) {
            assertFailsWith<IllegalStateException> {
                cache.getOrLoad(sourceId, SourceOperation.DETAILS, "42", 1_000) {
                    loads += 1
                    error("broken")
                }
            }
        }

        assertEquals(2, loads)
    }
}
