package org.akkirrai.beakokit.metadata

import io.ktor.client.HttpClient
import org.akkirrai.beakokit.model.AnimeTitle

/**
 * Where a title actually gets described: which provider to ask, how a source title is bound to one
 * of that provider's entries, and what is cached so neither question is asked twice.
 *
 * The clients only make requests; the rules for scoring a match and merging fields are pure and live
 * in `ExternalMetadata.kt`. This class owns everything stateful in between, and mirrors the desktop
 * client's `main/metadata/externalMetadataService.ts`.
 */
class ExternalMetadataService(
    client: HttpClient,
    private val store: ExternalMetadataStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val anilist = AniListClient(client)
    private val mal = MalClient(client)
    private val kitsu = KitsuClient(client)

    /**
     * Describes one title from the first provider in [order] that can, trying the next when one
     * cannot.
     *
     * Never throws: a caller merges whatever comes back, and null simply means the screen keeps the
     * source's own metadata.
     */
    suspend fun metadataFor(anime: AnimeTitle, order: List<MetadataProviderId>): ExternalMetadata? {
        for (provider in order) {
            val media = runCatching { metadataFromProvider(anime, provider) }.getOrNull()
            if (media != null) return media
        }
        return null
    }

    /**
     * What is already stored for a title, with no request at all - the first provider in [order]
     * that has a usable binding and a cached entry for it.
     *
     * A list screen asks this: describing a screenful over the network would be a request per title
     * at roughly one a second, and the screen would finish painting long before they returned.
     */
    fun cachedMetadataFor(titleId: String, order: List<MetadataProviderId>): ExternalMetadata? {
        for (provider in order) {
            val externalId = store.readMatch(titleId, provider)?.externalId ?: continue
            store.readMedia(provider, externalId)?.media?.let { return it }
        }
        return null
    }

    /**
     * What currently describes a title, for the screen's own "metadata" line - the first provider in
     * [order] with a usable binding, and whether the user set it by hand.
     */
    fun bindingFor(titleId: String, order: List<MetadataProviderId>): MetadataMatchRecord? {
        for (provider in order) {
            val record = store.readMatch(titleId, provider) ?: continue
            if (record.externalId != null) return record
        }
        return null
    }

    /** One entry by id or by Kitsu slug, for the picker's paste-a-link path - the way a title gets
     * rebound while a provider's search is down. */
    suspend fun entryFor(reference: MetadataReference): ExternalMetadata? {
        val media = when {
            reference.slug != null && reference.provider == MetadataProviderId.KITSU -> kitsu.fetchBySlug(reference.slug!!)
            reference.externalId != null -> fetchById(reference.provider, reference.externalId!!)
            else -> null
        }
        if (media != null) store.writeMedia(media, nowMillis())
        return media ?: reference.externalId?.let { store.readMedia(reference.provider, it)?.media }
    }

    /** Binds a title to a provider entry by hand. Marked manual, which is what stops the automatic
     * matcher from ever overwriting it again. */
    suspend fun setManualMatch(titleId: String, provider: MetadataProviderId, externalId: Int): ExternalMetadata? {
        val media = fetchById(provider, externalId) ?: store.readMedia(provider, externalId)?.media
        if (media != null) store.writeMedia(media, nowMillis())
        store.writeMatch(
            MetadataMatchRecord(titleId, provider, externalId, confidencePercent = null, manual = true, matchedAtMillis = nowMillis()),
        )
        return media
    }

    /** Drops a title's bindings for every provider, so the next lookup matches it from scratch. */
    fun clearMatch(titleId: String) = store.clearMatches(titleId)

    /** Candidates for a manual picker, from the first provider in [order] whose search answers. */
    suspend fun search(query: String, order: List<MetadataProviderId>): List<ExternalMetadata> {
        for (provider in order) {
            val results = runCatching { searchProvider(provider, query) }.getOrNull() ?: continue
            for (entry in results) store.writeMedia(entry.media, nowMillis())
            return results.map(ScoredEntry::media)
        }
        return emptyList()
    }

    private suspend fun metadataFromProvider(anime: AnimeTitle, provider: MetadataProviderId): ExternalMetadata? {
        val titleId = anime.id
        val match = store.readMatch(titleId, provider)

        if (match != null) {
            if (match.externalId == null) {
                // A remembered failure. Manual "no match" is not a thing, so only the TTL retires it -
                // without this, every visit to a title the provider does not have re-runs the same
                // fruitless search.
                if (nowMillis() - match.matchedAtMillis < TTL_NO_MATCH_MILLIS) return null
            } else {
                val cached = store.readMedia(provider, match.externalId)
                if (cached != null && nowMillis() - cached.cachedAtMillis < ttlFor(cached.media)) return cached.media
                val refreshed = fetchById(provider, match.externalId)
                if (refreshed != null) {
                    store.writeMedia(refreshed, nowMillis())
                    recordCrossMatches(titleId, refreshed)
                    return refreshed
                }
                // Offline, or the provider is down. A stale entry beats an empty screen.
                return cached?.media
            }
        }

        crossLookup(titleId, provider)?.let { crossMatched ->
            store.writeMedia(crossMatched, nowMillis())
            store.writeMatch(
                MetadataMatchRecord(titleId, provider, crossMatched.externalId, null, manual = false, matchedAtMillis = nowMillis()),
            )
            recordCrossMatches(titleId, crossMatched)
            return crossMatched
        }

        // Three shots at most - each is a request, and a title that has not turned up by then is very
        // likely simply absent. See searchQueriesFor for why the later ones are worth spending.
        var searched = false
        for (query in searchQueriesFor(anime, MAX_SEARCHES_PER_PROVIDER)) {
            // Null means the request itself failed - unreachable, rate-limited, or (as AniList was
            // while this was written) disabled outright. Give up on this provider and leave no
            // record: a remembered "no match" is a week-long statement about the *title*.
            val results = searchProvider(provider, query) ?: return null
            searched = true
            val best = pickBestMatch(anime, results.map(ScoredEntry::candidate)) ?: continue
            val found = results.firstOrNull { it.media.externalId == best.externalId }?.media ?: continue
            store.writeMedia(found, nowMillis())
            store.writeMatch(
                MetadataMatchRecord(
                    titleId,
                    provider,
                    best.externalId,
                    (best.confidence * 100).toInt(),
                    manual = false,
                    matchedAtMillis = nowMillis(),
                ),
            )
            recordCrossMatches(titleId, found)
            return found
        }

        if (searched) {
            store.writeMatch(
                MetadataMatchRecord(titleId, provider, externalId = null, confidencePercent = null, manual = false, matchedAtMillis = nowMillis()),
            )
        }
        return null
    }

    /**
     * Establishes this provider's entry from what another provider already knows, rather than by
     * searching for the name again.
     *
     * Worth a request of its own because search is the fragile, heavily rate-limited half of every
     * one of these APIs, and the half that guesses; a lookup by id is neither. AniList and Kitsu both
     * index MAL ids and both publish one, so a title matched through any provider can be bound to the
     * others exactly.
     */
    private suspend fun crossLookup(titleId: String, provider: MetadataProviderId): ExternalMetadata? {
        val malId = knownMalId(titleId) ?: return null
        return when (provider) {
            MetadataProviderId.MAL -> mal.fetchById(malId)
            MetadataProviderId.ANILIST -> anilist.fetchByMalId(malId)
            MetadataProviderId.KITSU -> kitsu.fetchByMalId(malId)
        }
    }

    /** The MAL id this title is already known by, from any provider matched to it - the common
     * currency between all three. */
    private fun knownMalId(titleId: String): Int? {
        for (record in store.readMatches(titleId)) {
            val externalId = record.externalId ?: continue
            if (record.provider == MetadataProviderId.MAL) return externalId
            store.readMedia(record.provider, externalId)?.media?.malId?.let { return it }
        }
        return null
    }

    /** Records the cross-provider ids an entry handed us as the other providers' matches, so a switch
     * or a fallback never repeats the search that established this one. Only ever fills a gap. */
    private fun recordCrossMatches(titleId: String, media: ExternalMetadata) {
        val pairs = listOf(
            MetadataProviderId.ANILIST to media.anilistId,
            MetadataProviderId.MAL to media.malId,
            MetadataProviderId.KITSU to media.kitsuId,
        )
        for ((provider, externalId) in pairs) {
            if (provider == media.provider || externalId == null) continue
            if (store.readMatch(titleId, provider) != null) continue
            store.writeMatch(
                MetadataMatchRecord(titleId, provider, externalId, confidencePercent = null, manual = false, matchedAtMillis = nowMillis()),
            )
        }
    }

    private suspend fun fetchById(provider: MetadataProviderId, externalId: Int): ExternalMetadata? = when (provider) {
        MetadataProviderId.ANILIST -> anilist.fetchById(externalId)
        MetadataProviderId.MAL -> mal.fetchById(externalId)
        MetadataProviderId.KITSU -> kitsu.fetchById(externalId)
    }

    private suspend fun searchProvider(provider: MetadataProviderId, query: String): List<ScoredEntry>? = when (provider) {
        MetadataProviderId.ANILIST -> anilist.search(query)
        MetadataProviderId.MAL -> mal.search(query)
        MetadataProviderId.KITSU -> kitsu.search(query)
    }

    private fun ttlFor(media: ExternalMetadata): Long =
        if (media.status == "released") TTL_SETTLED_MILLIS else TTL_AIRING_MILLIS

    private companion object {
        // A finished show's metadata is effectively frozen; an airing one moves every week and
        // carries the next-episode countdown the details screen prints.
        const val TTL_SETTLED_MILLIS = 14L * 24 * 60 * 60 * 1_000
        const val TTL_AIRING_MILLIS = 12L * 60 * 60 * 1_000
        // Short enough that an entry a provider adds later is picked up within a week.
        const val TTL_NO_MATCH_MILLIS = 7L * 24 * 60 * 60 * 1_000
        const val MAX_SEARCHES_PER_PROVIDER = 3
    }
}
