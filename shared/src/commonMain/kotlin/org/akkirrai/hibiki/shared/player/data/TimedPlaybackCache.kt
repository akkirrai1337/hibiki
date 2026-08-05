package org.akkirrai.hibiki.shared.player

class TimedPlaybackCache<K, V>(
    private val ttlMillis: Long,
    private val nowMillis: () -> Long,
) {
    private val entries = mutableMapOf<K, Entry<V>>()

    fun get(key: K): V? {
        removeExpired()
        return entries[key]?.value
    }

    fun put(key: K, value: V) {
        removeExpired()
        entries[key] = Entry(value, nowMillis())
    }

    fun clear() {
        entries.clear()
    }

    private fun removeExpired() {
        val now = nowMillis()
        entries.entries.removeAll { (_, entry) -> now - entry.createdAt >= ttlMillis }
    }

    private data class Entry<V>(val value: V, val createdAt: Long)
}
