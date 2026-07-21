package org.akkirrai.beakokit.api

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

object SourceCacheTtl {
    const val SEARCH_MILLIS = 60_000L
    const val DETAILS_MILLIS = 5 * 60_000L
    const val FILTER_CATALOG_MILLIS = 30 * 60_000L
    const val LATEST_MILLIS = 60_000L
    const val SCHEDULE_MILLIS = 5 * 60_000L
    const val PLAYBACK_GROUPS_MILLIS = 60_000L
}

data class SourceResultCachePolicy(
    val maxEntries: Int = 200,
) {
    init {
        require(maxEntries > 0) { "Source result cache size must be positive" }
    }
}

private data class SourceResultCacheKey(
    val sourceId: SourceId,
    val operation: SourceOperation,
    val key: String,
)

private data class CachedSourceResult(
    val value: Any?,
    val expiresAtMillis: Long,
    val createdAtMillis: Long,
)

/** Thread-safe bounded cache with single-flight loading for public, safe-to-reuse source results. */
class SourceResultCache(
    private val policy: SourceResultCachePolicy = SourceResultCachePolicy(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = ConcurrentHashMap<SourceResultCacheKey, CachedSourceResult>()
    private val inFlight = ConcurrentHashMap<SourceResultCacheKey, CompletableDeferred<Any?>>()

    suspend fun <T> getOrLoad(
        sourceId: SourceId,
        operation: SourceOperation,
        key: String,
        ttlMillis: Long,
        loader: suspend () -> T,
    ): T {
        require(key.isNotBlank()) { "Source cache key must not be blank" }
        require(ttlMillis > 0) { "Source cache TTL must be positive" }
        val cacheKey = SourceResultCacheKey(sourceId, operation, key)
        read(cacheKey)?.let { cached ->
            @Suppress("UNCHECKED_CAST")
            return cached.value as T
        }

        val deferred = CompletableDeferred<Any?>()
        val existing = inFlight.putIfAbsent(cacheKey, deferred)
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing.await() as T
        }

        try {
            val value = loader()
            write(cacheKey, value, ttlMillis)
            deferred.complete(value)
            return value
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(cacheKey, deferred)
        }
    }

    fun invalidate(sourceId: SourceId) {
        entries.keys.removeIf { it.sourceId == sourceId }
    }

    fun clear() = entries.clear()

    private fun read(key: SourceResultCacheKey): CachedSourceResult? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= nowMillis()) {
            entries.remove(key, entry)
            return null
        }
        return entry
    }

    private fun write(key: SourceResultCacheKey, value: Any?, ttlMillis: Long) {
        val now = nowMillis()
        entries.entries.removeIf { (_, entry) -> entry.expiresAtMillis <= now }
        if (entries.size >= policy.maxEntries && !entries.containsKey(key)) {
            entries.entries.minByOrNull { (_, entry) -> entry.createdAtMillis }?.let { oldest ->
                entries.remove(oldest.key, oldest.value)
            }
        }
        entries[key] = CachedSourceResult(value, now + ttlMillis, now)
    }
}

/** Adds caching only to operations whose source explicitly provides a safe cache key and TTL. */
class CachingSourceExecutionPolicy(
    private val delegate: SourceExecutionPolicy,
    private val cache: SourceResultCache = SourceResultCache(),
) : SourceExecutionPolicy {
    override suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        block: suspend () -> T,
    ): T = delegate.execute(sourceId, operation, block)

    override suspend fun <T> execute(
        sourceId: SourceId,
        operation: SourceOperation,
        cacheKey: String,
        cacheTtlMillis: Long,
        block: suspend () -> T,
    ): T = cache.getOrLoad(sourceId, operation, cacheKey, cacheTtlMillis) {
        delegate.execute(sourceId, operation, block)
    }

    fun invalidate(sourceId: SourceId) = cache.invalidate(sourceId)

    fun clear() = cache.clear()
}
