package org.akkirrai.beakokit.metadata

/**
 * Where [ExternalMetadataService] keeps what it has learned. Three separate things on purpose:
 *
 * - a *match* costs a search request plus scoring and is worth keeping for good;
 * - the *media* behind it is a cache line that can be thrown away and refetched, and one entry backs
 *   the same show on every installed source, so it is stored once by provider and id;
 * - a *failed* search is worth remembering too, or every visit to a title a provider does not have
 *   re-runs it.
 *
 * An interface rather than a concrete store because parsers stays platform-free; the host provides
 * the persistence it already uses.
 */
interface ExternalMetadataStore {
    fun readMatch(titleId: String, provider: MetadataProviderId): MetadataMatchRecord?

    fun writeMatch(record: MetadataMatchRecord)

    /** Every recorded match for one title, across providers - what a cross-provider lookup reads. */
    fun readMatches(titleId: String): List<MetadataMatchRecord>

    fun clearMatches(titleId: String)

    /** Every match pointing at one provider entry, within one source - the match table read
     * backwards, which is how a catalog browsed from the aggregator finds something to play. */
    fun matchesForEntry(sourceId: String, provider: MetadataProviderId, externalId: Int): List<MetadataMatchRecord>

    /** Whether a completed search of this source found nothing for this entry, and when. Its own
     * record because the match table is keyed by the source title id - which is precisely what a
     * failed resolution does not have. */
    fun readUnresolvedAt(sourceId: String, provider: MetadataProviderId, externalId: Int): Long?

    fun writeUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int, attemptedAtMillis: Long)

    fun clearUnresolved(sourceId: String, provider: MetadataProviderId, externalId: Int)

    fun readMedia(provider: MetadataProviderId, externalId: Int): CachedMetadata?

    fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long)
}

/**
 * Which entry of which provider a source title was matched to.
 *
 * [manual] marks a binding the user fixed by hand. Automatic re-matching must never overwrite one -
 * a wrong match on a sequel or a recap is exactly the case that button exists to fix.
 */
data class MetadataMatchRecord(
    /** Source-scoped title id (see [org.akkirrai.beakokit.api.AnimeKey]). */
    val titleId: String,
    val provider: MetadataProviderId,
    /** Null records a *failed* search, which is worth remembering. */
    val externalId: Int?,
    /** 0..100, null for a manual binding, which is certain by definition. */
    val confidencePercent: Int?,
    val manual: Boolean,
    val matchedAtMillis: Long,
)

/** A stored provider entry, with the timestamp that lets the service decide whether it is stale
 * without a round trip. */
data class CachedMetadata(
    val media: ExternalMetadata,
    val cachedAtMillis: Long,
)
