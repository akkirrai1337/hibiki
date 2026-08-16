package org.akkirrai.beakokit.api

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
