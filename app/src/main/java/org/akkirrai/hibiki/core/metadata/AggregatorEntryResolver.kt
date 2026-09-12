package org.akkirrai.hibiki.core.metadata

import org.akkirrai.hibiki.core.model.Anime

/**
 * Resolves cards from an aggregator's own catalog against a source, so an aggregator-driven catalog
 * page can be opened as an ordinary title. Implemented by any repository that offers such a page
 * (currently `CatalogRepository`, and `HomeRepository` for its trending row).
 */
interface AggregatorEntryResolver {
    /** Turns a card from the aggregator catalog into the source's own title for this entry, or null. */
    suspend fun resolveEntry(anime: Anime): Anime?

    /** Binds an entry to a title of this source by hand, from the resolution sheet, and opens it. */
    suspend fun bindEntry(anime: Anime, titleId: String): Anime?

    /** The source's own results for a query, for that sheet to choose from. */
    suspend fun searchSourceTitles(query: String): List<Anime>
}
