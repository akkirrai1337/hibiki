package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.model.AnimeTitle

/** Stable source-owned capabilities used by a host before executing an operation. */
@Serializable
enum class SourceCapability {
    LATEST_RELEASES,
    PLAYBACK,
    RELATED_TITLES,
    SIMILAR_TITLES,

    // What a source can do once someone is signed in to it. Each one gates a piece of UI, so a
    // source that declares none of them looks exactly as it does today. Kept in step with the
    // desktop's own SourceCapability union (hibiki-desktop `src/shared/types.ts`), since both read
    // the same manifests.
    ACCOUNT,
    COMMENTS,
    REVIEWS,
    LIBRARY_SYNC,
}

/** Optional capability for sources that expose their latest updated titles. */
fun interface LatestSource {
    suspend fun latest(limit: Int): List<AnimeTitle>
}
