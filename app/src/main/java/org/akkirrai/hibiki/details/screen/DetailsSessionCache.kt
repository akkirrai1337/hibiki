package org.akkirrai.hibiki.details.screen

private const val DETAILS_SESSION_CACHE_LIMIT = 20

/**
 * Small per-anime cache for Details UI state that needs to survive navigating away and back
 * (scroll position, extracted title seed color) even though the composable itself gets fully
 * disposed and recreated on each visit. Bounded and least-recently-used so a session that visits
 * many different titles doesn't retain state for all of them forever.
 */
internal class DetailsSessionCache<T> {
    private val entries = object : LinkedHashMap<String, T>(DETAILS_SESSION_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>) =
            size > DETAILS_SESSION_CACHE_LIMIT
    }

    operator fun get(animeId: String): T? = entries[animeId]
    operator fun set(animeId: String, value: T) {
        entries[animeId] = value
    }
}
