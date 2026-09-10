package org.akkirrai.beakokit.metadata

/**
 * Where [AniListMetadataProvider] keeps what it has learned. Two separate things on purpose: a
 * *match* costs a search request plus scoring and is worth keeping for good, while the *media*
 * behind it is a cache line that can be thrown away and refetched - and one AniList entry backs the
 * same show on every installed source, so it is stored once by its own id rather than per title.
 *
 * An interface rather than a concrete store because parsers stays platform-free; the host provides
 * the persistence it already uses ([org.akkirrai.hibiki] backs this with SharedPreferences, the
 * desktop client with two SQLite tables).
 */
interface AniListMetadataStore {
    fun readMatch(titleId: String): MetadataMatchRecord?

    fun writeMatch(record: MetadataMatchRecord)

    fun clearMatch(titleId: String)

    fun readMedia(anilistId: Int): CachedMetadata?

    fun writeMedia(media: ExternalMetadata, cachedAtMillis: Long)
}

/**
 * Which AniList entry a source title was matched to.
 *
 * [manual] marks a binding the user fixed by hand on the details screen. Automatic re-matching must
 * never overwrite one - a wrong match on a sequel or a recap is exactly the case the user reached
 * for that button to fix, and silently undoing it on the next refresh would make the button
 * useless.
 */
data class MetadataMatchRecord(
    /** Source-scoped title id (see [org.akkirrai.beakokit.api.AnimeKey]), so two sources carrying
     * the same show are matched independently. */
    val titleId: String,
    /** Null records a *failed* search, which is worth remembering: without it every visit to a
     * title AniList simply does not have re-runs the same fruitless search. */
    val anilistId: Int?,
    /** 0..100, null for a manual binding, which is certain by definition. */
    val confidencePercent: Int?,
    val manual: Boolean,
    val matchedAtMillis: Long,
)

/** A stored AniList entry, with the timestamp that lets the provider decide whether it is stale
 * without a round trip. */
data class CachedMetadata(
    val media: ExternalMetadata,
    val cachedAtMillis: Long,
)
