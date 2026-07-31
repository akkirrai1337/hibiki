package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime

/** Optional platform cache for title details used before the catalog refresh completes. */
interface OfflineTitleMetadataRepository {
    fun get(id: String): Anime?

    fun save(anime: Anime)
}
