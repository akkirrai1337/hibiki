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
