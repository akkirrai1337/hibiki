package org.akkirrai.hibiki.details.data

import org.akkirrai.hibiki.catalog.model.Anime

/** Optional platform cache for title details used before the catalog refresh completes. */
interface OfflineTitleMetadataRepository {
    fun get(id: String): Anime?

    fun save(anime: Anime)
}
