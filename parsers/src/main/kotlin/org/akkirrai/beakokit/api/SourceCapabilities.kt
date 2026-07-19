package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeTitle

/** Stable source-owned capabilities used by a host before executing an operation. */
enum class SourceCapability {
    LATEST_RELEASES,
    PLAYBACK,
    RELATED_TITLES,
    SIMILAR_TITLES,
}

/** Optional capability for sources that expose their latest updated titles. */
fun interface LatestSource {
    suspend fun latest(limit: Int): List<AnimeTitle>
}
