package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.prototype.PrototypeCatalog

/** Platform-neutral catalog boundary consumed by shared screens. */
interface AnimeCatalogRepository {
    val initialItems: List<Anime>

    suspend fun search(query: String): List<Anime>
}

/** Temporary deterministic data source for the Windows prototype. */
object PrototypeAnimeCatalogRepository : AnimeCatalogRepository {
    override val initialItems: List<Anime> = PrototypeCatalog.items

    override suspend fun search(query: String): List<Anime> =
        if (query.isBlank()) {
            initialItems
        } else {
            initialItems.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true) ||
                    it.genres.any { genre -> genre.contains(query, ignoreCase = true) }
            }
        }
}
