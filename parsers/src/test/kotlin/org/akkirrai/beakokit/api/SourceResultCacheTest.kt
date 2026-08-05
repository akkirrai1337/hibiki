package org.akkirrai.beakokit.api

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
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

    @Test
    fun `cache saturates expiry instead of overflowing`() = runBlocking {
        val cache = SourceResultCache(nowMillis = { Long.MAX_VALUE - 1 })
        var loads = 0

        suspend fun load() = cache.getOrLoad(sourceId, SourceOperation.DETAILS, "overflow", 10) {
            ++loads
            "value"
        }

        assertEquals("value", load())
        assertEquals("value", load())
        assertEquals(1, loads)
    }

    @Test
    fun `source invalidation prevents an in-flight result from being cached`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = SourceResultCache()
        var loads = 0

        val first = async {
            cache.getOrLoad(sourceId, SourceOperation.DETAILS, "stale", 1_000) {
                loads += 1
                started.complete(Unit)
                release.await()
                "stale"
            }
        }
        started.await()
        cache.invalidate(sourceId)
        release.complete(Unit)
        assertEquals("stale", first.await())

        assertEquals(
            "fresh",
            cache.getOrLoad(sourceId, SourceOperation.DETAILS, "stale", 1_000) {
                loads += 1
                "fresh"
            },
        )
        assertEquals(2, loads)
    }

    @Test
    fun `clear prevents an in-flight result from being cached`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = SourceResultCache()
        var loads = 0

        val first = async {
            cache.getOrLoad(sourceId, SourceOperation.DETAILS, "clear-me", 1_000) {
                loads += 1
                started.complete(Unit)
                release.await()
                "stale"
            }
        }
        started.await()
        cache.clear()
        release.complete(Unit)
        assertEquals("stale", first.await())

        assertEquals(
            "fresh",
            cache.getOrLoad(sourceId, SourceOperation.DETAILS, "clear-me", 1_000) {
                loads += 1
                "fresh"
            },
        )
        assertEquals(2, loads)
    }
}
