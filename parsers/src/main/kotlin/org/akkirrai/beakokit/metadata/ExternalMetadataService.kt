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
/** How a resolution was reached, so a screen can be honest about a match decided by name alone. */
enum class ResolvedVia { RECORDED, CROSS_PROVIDER, SEARCH }

/** Which title of a source a provider entry resolved to. */
data class ResolvedSourceTitle(
    val titleId: String,
    /** 0..100 for a match the app made, null for one the user set by hand. */
    val confidencePercent: Int?,
    val manual: Boolean,
    val via: ResolvedVia,
)

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

    /**
     * A page of a provider's catalog, from the first browsable provider in [order] that answers.
     *
     * Not every provider can be browsed (see [CATALOG_PROVIDERS]), and the ones that can go down
     * independently, so this walks the same order the describing path uses and reports which one
     * actually answered. Entries are cached on the way past, which is what makes opening one of
     * these cards resolve without another request.
     */
    suspend fun browse(
        request: ExternalCatalogRequest,
        order: List<MetadataProviderId>,
    ): Pair<List<ExternalMetadata>, MetadataProviderId?> {
        for (provider in order.filter { it in CATALOG_PROVIDERS }) {
            val results = runCatching {
                when (provider) {
                    MetadataProviderId.ANILIST -> anilist.browse(request)
                    MetadataProviderId.KITSU -> kitsu.browse(request)
                    MetadataProviderId.MAL -> null
                }
            }.getOrNull() ?: continue
            for (media in results) store.writeMedia(media, nowMillis())
            return results to provider
        }
        return emptyList<ExternalMetadata>() to null
    }

    /**
     * Which title of a source is a given provider entry: the reverse direction, for a catalog
     * browsed from the aggregator and resolved to something playable only when a title is opened.
     *
     * Four tiers, cheapest first, and only the third costs a request:
     *
     * 1. A match already recorded - everything ever opened, described on a screen, or kept in the
     *    library is in that table already, so this is the common case by a wide margin.
     * 2. The same, reached through another provider's id.
     * 3. A live search of the source, scored by the same rules as the forward direction and recorded
     *    afterwards, which makes it tier 1 from then on.
     * 4. The user picks - this returns null and the screen offers the source's own results.
     */
    suspend fun resolveSourceTitle(
        sourceId: String,
        entry: ExternalMetadata,
        searchSource: suspend (String) -> List<AnimeTitle>?,
    ): ResolvedSourceTitle? {
        recordedSourceTitle(sourceId, entry.provider, entry.externalId)?.let { return it }
        for ((provider, externalId) in crossIdsOf(entry)) {
            recordedSourceTitle(sourceId, provider, externalId)?.let {
                return it.copy(via = ResolvedVia.CROSS_PROVIDER)
            }
        }

        // A recent search of this source already came back with nothing for this entry. Re-running it
        // on every visit to the same card is two requests to be told the same thing.
        val unresolvedAt = store.readUnresolvedAt(sourceId, entry.provider, entry.externalId)
        if (unresolvedAt != null && nowMillis() - unresolvedAt < TTL_UNRESOLVED_MILLIS) return null

        for (query in sourceSearchQueriesFor(entry)) {
            // Null is the source being unreachable rather than lacking the title - say nothing,
            // record nothing, and let the screen offer to try again or pick by hand.
            val results = runCatching { searchSource(query) }.getOrNull() ?: return null
            val best = pickSourceTitleFor(entry, results) ?: continue
            val confidence = (best.second * 100).toInt()
            store.writeMedia(entry, nowMillis())
            store.writeMatch(
                MetadataMatchRecord(best.first, entry.provider, entry.externalId, confidence, manual = false, matchedAtMillis = nowMillis()),
            )
            recordCrossMatches(best.first, entry)
            store.clearUnresolved(sourceId, entry.provider, entry.externalId)
            return ResolvedSourceTitle(best.first, confidence, manual = false, via = ResolvedVia.SEARCH)
        }
        // Every query ran and none of them found it.
        store.writeUnresolved(sourceId, entry.provider, entry.externalId, nowMillis())
        return null
    }

    /** Binds a provider entry to a title of this source by hand, from the resolution screen. */
    fun setManualSourceTitle(sourceId: String, titleId: String, entry: ExternalMetadata) {
        store.writeMedia(entry, nowMillis())
        store.writeMatch(
            MetadataMatchRecord(titleId, entry.provider, entry.externalId, confidencePercent = null, manual = true, matchedAtMillis = nowMillis()),
        )
        recordCrossMatches(titleId, entry)
        store.clearUnresolved(sourceId, entry.provider, entry.externalId)
    }

    private fun crossIdsOf(entry: ExternalMetadata): List<Pair<MetadataProviderId, Int>> = listOfNotNull(
        entry.anilistId?.let { MetadataProviderId.ANILIST to it },
        entry.malId?.let { MetadataProviderId.MAL to it },
        entry.kitsuId?.let { MetadataProviderId.KITSU to it },
    ).filterNot { it.first == entry.provider }

    /** The recorded match for one entry, read backwards. A "no match" row can never be selected
     * here, since it is keyed by a null this query never asks for. */
    private fun recordedSourceTitle(sourceId: String, provider: MetadataProviderId, externalId: Int): ResolvedSourceTitle? {
        val matches = store.matchesForEntry(sourceId, provider, externalId)
        // A source can carry the same show twice (a dub entry beside a subbed one), and both may have
        // been matched to this entry. A binding the user made by hand is the one they meant.
        val record = matches.firstOrNull(MetadataMatchRecord::manual) ?: matches.firstOrNull() ?: return null
        return ResolvedSourceTitle(record.titleId, record.confidencePercent, record.manual, ResolvedVia.RECORDED)
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

        // How long a failed *resolution* is remembered - a day, against the week a failed
        // description gets. A source's catalog gains titles far faster than an aggregator gains
        // entries.
        const val TTL_UNRESOLVED_MILLIS = 24L * 60 * 60 * 1_000
    }
}
