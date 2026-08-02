package org.akkirrai.beakokit.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

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
    private val nowMillis: () -> Long = ::currentWallClockMillis,
) {
    private val entries = MutableStateFlow<Map<SourceResultCacheKey, CachedSourceResult>>(emptyMap())
    private val inFlight = MutableStateFlow<Map<SourceResultCacheKey, CompletableDeferred<Any?>>>(emptyMap())

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
        while (true) {
            val current = inFlight.value
            val existing = current[cacheKey]
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                return existing.await() as T
            }
            if (inFlight.compareAndSet(current, current + (cacheKey to deferred))) break
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
            while (true) {
                val current = inFlight.value
                if (current[cacheKey] !== deferred) break
                if (inFlight.compareAndSet(current, current - cacheKey)) break
            }
        }
    }

    fun invalidate(sourceId: SourceId) {
        while (true) {
            val current = entries.value
            val updated = current.filterKeys { it.sourceId != sourceId }
            if (entries.compareAndSet(current, updated)) return
        }
    }

    fun clear() {
        entries.value = emptyMap()
    }

    private fun read(key: SourceResultCacheKey): CachedSourceResult? {
        while (true) {
            val current = entries.value
            val entry = current[key] ?: return null
            if (entry.expiresAtMillis > nowMillis()) return entry
            if (entries.compareAndSet(current, current - key)) return null
        }
    }

    private fun write(key: SourceResultCacheKey, value: Any?, ttlMillis: Long) {
        val now = nowMillis()
        while (true) {
            val current = entries.value
            val valid = current.filterValues { it.expiresAtMillis > now }
            val bounded = if (valid.size >= policy.maxEntries && !valid.containsKey(key)) {
                valid - valid.minByOrNull { (_, entry) -> entry.createdAtMillis }!!.key
            } else {
                valid
            }
            val updated = bounded + (key to CachedSourceResult(value, now + ttlMillis, now))
            if (entries.compareAndSet(current, updated)) return
        }
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
