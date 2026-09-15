package org.akkirrai.beakokit.metadata

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    /** Diagnostic trail for why a title did or didn't get described - which provider was tried, why
     * a candidate was rejected, how many requests it cost. No-op by default; the app wires this to
     * its own logger so this module stays free of any platform logging dependency. */
    private val log: (String) -> Unit = {},
    /** The app's MAL API client id. With one, MAL is read through its official API and Jikan only
     * covers for it; without one, Jikan alone. */
    malClientId: String? = null,
) {
    private val anilist = AniListClient(client)
    private val mal = MalClient(client, malClientId)
    private val kitsu = KitsuClient(client)
    private val inFlight = ConcurrentHashMap<String, InFlightLookup>()
    /** An aggregator card is often tapped twice while navigation is starting. Keep that from
     * becoming two identical provider reads when its cached entry has expired or was evicted. */
    private val inFlightEntries = ConcurrentHashMap<String, CompletableDeferred<ExternalMetadata?>>()
    private val pendingRefreshes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + MetadataPriority.Prefetch)

    /**
     * Describes one title from [order]'s providers, in two passes.
     *
     * The first pass only reads what is already on record - a match written when a card was
     * resolved from an aggregator's catalog, say - across *every* provider in [order], not just the
     * first. Only once none of them has anything recorded does the second pass fall back to a live
     * search, again in [order]. Without this split, a title already pinned to (say) Kitsu by the
     * entry screen could still be re-guessed from AniList because AniList happens to be preferred -
     * landing on a different show with a similar name and showing a different cover than the one
     * just tapped.
     *
     * Never throws: a caller merges whatever comes back, and null simply means the screen keeps the
     * source's own metadata.
     */
    suspend fun metadataFor(anime: AnimeTitle, order: List<MetadataProviderId>): ExternalMetadata? {
        // One lookup per title at a time: Home, Catalog and a details page each have their own
        // repository, and all of them can ask for the same title at once.
        val background = currentCoroutineContext()[MetadataPriority]?.background == true
        while (true) {
            val mine = InFlightLookup(background)
            val existing = inFlight.putIfAbsent(anime.id, mine)
            if (existing == null) {
                try {
                    return lookUpMetadata(anime, order).also { mine.result.complete(it) }
                } catch (error: Throwable) {
                    mine.result.completeExceptionally(error)
                    throw error
                } finally {
                    inFlight.remove(anime.id, mine)
                }
            }
            // A foreground caller must not wait in a background lookup's lane - it runs its own.
            if (existing.background && !background) return lookUpMetadata(anime, order)
            try {
                return existing.result.await()
            } catch (error: CancellationException) {
                // Either this caller was cancelled, or the lookup's owner was - then take it over.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private class InFlightLookup(val background: Boolean) {
        val result = CompletableDeferred<ExternalMetadata?>()
    }

    private suspend fun lookUpMetadata(anime: AnimeTitle, order: List<MetadataProviderId>): ExternalMetadata? {
        val effectiveOrder = displayOrder(anime.id, order)
        // A provider that already has *anything* on record for this title - a match, or a still-fresh
        // "no match" - is settled and must not be re-guessed by a live search in the second pass below.
        val settled = mutableSetOf<MetadataProviderId>()
        for (provider in effectiveOrder) {
            val recorded = runCatching { recordedMetadataFor(anime, provider) }
                .onFailure { log("metadataFor: provider=$provider threw for '${anime.englishName ?: anime.originalName}': ${it.message}") }
                .getOrNull() ?: RecordedResult.Unrecorded
            if (recorded is RecordedResult.Unrecorded) continue
            settled += provider
            (recorded as? RecordedResult.Found)?.let { return it.media }
        }
        for (provider in liveSearchOrder(effectiveOrder)) {
            if (provider in settled) continue
            val media = runCatching { liveSearchMetadataFor(anime, provider) }
                .onFailure { log("metadataFor: provider=$provider threw for '${anime.englishName ?: anime.originalName}': ${it.message}") }
                .getOrNull()
            if (media != null) return media
        }
        return null
    }

    /** Result of checking what is already on record for a provider, without a live search. */
    private sealed interface RecordedResult {
        /** A usable entry was found (a live match, a refresh, a cross-provider lookup, or a stale
         * cached copy served because the provider is unreachable). */
        data class Found(val media: ExternalMetadata) : RecordedResult
        /** A search already ran and confirmed, within its TTL, that this provider has nothing. */
        data object Confirmed : RecordedResult
        /** Nothing recorded yet either way - a live search is the only way left to answer. */
        data object Unrecorded : RecordedResult
    }

    /**
     * What is already stored for a title, with no request at all - the first provider in [order]
     * that has a usable binding and a cached entry for it.
     *
     * A list screen asks this: describing a screenful over the network would be a request per title
     * at roughly one a second, and the screen would finish painting long before they returned.
     */
    fun cachedMetadataFor(titleId: String, order: List<MetadataProviderId>): ExternalMetadata? {
        return cachedMetadataForAll(listOf(titleId), order)[titleId]
    }

    /** Fetches AniList's one-hop franchise graph only for an already-matched title details page.
     * List cards stay on the small aliased-search query. */
    suspend fun franchiseFor(anime: AnimeTitle, order: List<MetadataProviderId>): ExternalMetadata? {
        val media = metadataFor(anime, order) ?: return null
        if (media.provider != MetadataProviderId.ANILIST || media.franchiseLoaded) return media
        val detailed = anilist.fetchDetailsById(media.externalId) ?: return media
        store.writeMedia(detailed, nowMillis())
        recordCrossMatches(anime.id, detailed)
        return detailed
    }

    /** Reads a whole visible list from the store in bulk. This stays entirely offline, just like
     * [cachedMetadataFor], but avoids multiplying Room reads by cards times providers. */
    fun cachedMetadataForAll(titleIds: List<String>, order: List<MetadataProviderId>): Map<String, ExternalMetadata> {
        val distinctIds = titleIds.distinct()
        if (distinctIds.isEmpty() || order.isEmpty()) return emptyMap()
        val matchesByTitle = store.readMatches(distinctIds)
        val pinnedProviders = store.readDisplayProviders(distinctIds)
        val candidates = distinctIds.associateWith { titleId ->
            val displayedOrder = pinnedProviders[titleId]?.takeIf { it in order }
                ?.let { pinned -> listOf(pinned) + order.filterNot { it == pinned } }
                ?: order
            displayedOrder.mapNotNull { provider ->
                matchesByTitle[titleId]
                    ?.firstOrNull { it.provider == provider }
                    ?.externalId
                    ?.let { externalId -> MetadataMediaKey(provider, externalId) }
            }
        }
        val media = store.readMediaBatch(candidates.values.flatten())
        return candidates.mapNotNull { (titleId, keys) ->
            keys.firstNotNullOfOrNull { key -> media[key]?.media }?.let { titleId to it }
        }.toMap()
    }

    /**
     * What currently describes a title, for the screen's own "metadata" line - the first provider in
     * [order] with a usable binding, and whether the user set it by hand.
     */
    fun bindingFor(titleId: String, order: List<MetadataProviderId>): MetadataMatchRecord? {
        for (provider in displayOrder(titleId, order)) {
            val record = store.readMatch(titleId, provider) ?: continue
            if (record.externalId != null) return record
        }
        return null
    }

    /** One entry by id or by Kitsu slug, for the picker's paste-a-link path - the way a title gets
     * rebound while a provider's search is down. */
    suspend fun entryFor(reference: MetadataReference): ExternalMetadata? {
        reference.externalId?.let { externalId ->
            val cached = store.readMedia(reference.provider, externalId)
            if (cached != null) {
                if (nowMillis() - cached.cachedAtMillis < ttlFor(cached.media)) {
                    log("entryFor: provider=${reference.provider} cache hit, externalId=$externalId")
                    return cached.media
                }
                // A catalog result already contains everything a resolver needs. Do not make
                // opening that card wait behind a provider's pacing queue merely to refresh it.
                log("entryFor: provider=${reference.provider} serving stale cache, externalId=$externalId")
                refreshEntryInBackground(reference.provider, externalId)
                return cached.media
            }
        }

        val key = when {
            reference.externalId != null -> "${reference.provider.id}:id:${reference.externalId}"
            reference.slug != null -> "${reference.provider.id}:slug:${reference.slug}"
            else -> return null
        }
        while (true) {
            val mine = CompletableDeferred<ExternalMetadata?>()
            val existing = inFlightEntries.putIfAbsent(key, mine)
            if (existing == null) {
                try {
                    val media = when {
                        reference.slug != null && reference.provider == MetadataProviderId.KITSU -> kitsu.fetchBySlug(reference.slug)
                        reference.externalId != null -> fetchById(reference.provider, reference.externalId)
                        else -> null
                    }
                    if (media != null) store.writeMedia(media, nowMillis())
                    mine.complete(media)
                    return media ?: reference.externalId?.let { store.readMedia(reference.provider, it)?.media }
                } catch (error: Throwable) {
                    mine.completeExceptionally(error)
                    throw error
                } finally {
                    inFlightEntries.remove(key, mine)
                }
            }
            try {
                return existing.await()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
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
        val eligible = order.filter { provider ->
            provider in CATALOG_PROVIDERS && (!request.hasFilters || provider in FILTERABLE_CATALOG_PROVIDERS)
        }
        for (provider in eligible) {
            val results = runCatching {
                when (provider) {
                    MetadataProviderId.ANILIST -> anilist.browse(request)
                    MetadataProviderId.KITSU -> kitsu.browse(request)
                    MetadataProviderId.MAL -> null
                }
            }.getOrNull() ?: continue
            store.writeMediaBatch(results, nowMillis())
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
        recordedSourceTitle(sourceId, entry.provider, entry.externalId)?.let { return it.displayedAs(entry) }
        for ((provider, externalId) in crossIdsOf(entry)) {
            recordedSourceTitle(sourceId, provider, externalId)?.let {
                return it.copy(via = ResolvedVia.CROSS_PROVIDER).displayedAs(entry)
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
            return ResolvedSourceTitle(best.first, confidence, manual = false, via = ResolvedVia.SEARCH).displayedAs(entry)
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
        store.writeDisplayProvider(titleId, entry.provider)
    }

    /**
     * Pins [entry]'s provider as the one this title is described by from now on: the card that was just
     * opened is what the title page has to show. Without it the title page asks the preferred provider
     * first, and a match already recorded there for the same show - Kitsu's, when the card came from
     * AniList - brings a different poster than the one that was tapped. The entry itself is recorded as
     * that provider's match too, so the title page reads it straight from the store.
     */
    private fun ResolvedSourceTitle.displayedAs(entry: ExternalMetadata): ResolvedSourceTitle {
        store.writeMedia(entry, nowMillis())
        store.writeMatch(
            MetadataMatchRecord(titleId, entry.provider, entry.externalId, confidencePercent, manual = false, matchedAtMillis = nowMillis()),
        )
        store.writeDisplayProvider(titleId, entry.provider)
        return this
    }

    /** [order] with the provider this title was last opened through moved to the front - see
     * [displayedAs]. Only reorders: a provider the user has not allowed stays out. */
    private fun displayOrder(titleId: String, order: List<MetadataProviderId>): List<MetadataProviderId> {
        val pinned = store.readDisplayProvider(titleId)?.takeIf { it in order } ?: return order
        return listOf(pinned) + order.filterNot { it == pinned }
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
        store.writeDisplayProvider(titleId, provider)
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

    /** What is already on record for this title and provider - a prior match, or one reachable
     * through another provider's id - with no live search. [RecordedResult.Unrecorded] means the
     * caller must fall back to [liveSearchMetadataFor]. */
    private suspend fun recordedMetadataFor(anime: AnimeTitle, provider: MetadataProviderId): RecordedResult {
        val titleId = anime.id
        val label = anime.englishName?.takeIf(String::isNotBlank) ?: anime.originalName
        val match = store.readMatch(titleId, provider)

        if (match != null) {
            if (match.externalId == null) {
                // A remembered failure. Manual "no match" is not a thing, so only the TTL retires it -
                // without this, every visit to a title the provider does not have re-runs the same
                // fruitless search.
                val remainingMillis = TTL_NO_MATCH_MILLIS - (nowMillis() - match.matchedAtMillis)
                if (remainingMillis > 0) {
                    log("recordedMetadataFor: '$label' provider=$provider skipped, remembered no-match (retries in ${remainingMillis / 60_000}m)")
                    return RecordedResult.Confirmed
                }
                return RecordedResult.Unrecorded
            }
            val cached = store.readMedia(provider, match.externalId)
            if (cached != null && nowMillis() - cached.cachedAtMillis < ttlFor(cached.media)) {
                log("recordedMetadataFor: '$label' provider=$provider cache hit, externalId=${match.externalId}")
                return RecordedResult.Found(cached.media)
            }
            if (cached != null) {
                // Stale: shown now, refreshed behind it. Waiting on the refresh held a whole title
                // page on a round trip for data that is at worst half a day old.
                log("recordedMetadataFor: '$label' provider=$provider serving stale copy of externalId=${match.externalId}, refreshing in background")
                refreshInBackground(titleId, provider, match.externalId)
                return RecordedResult.Found(cached.media)
            }
            val refreshed = fetchById(provider, match.externalId)
            if (refreshed != null) {
                log("recordedMetadataFor: '$label' provider=$provider fetched bound entry externalId=${match.externalId}")
                store.writeMedia(refreshed, nowMillis())
                recordCrossMatches(titleId, refreshed)
                return RecordedResult.Found(refreshed)
            }
            log("recordedMetadataFor: '$label' provider=$provider fetch failed for externalId=${match.externalId} and nothing cached either")
            return RecordedResult.Unrecorded
        }

        crossLookup(titleId, provider)?.let { crossMatched ->
            log("recordedMetadataFor: '$label' provider=$provider matched via another provider's id, externalId=${crossMatched.externalId}")
            store.writeMedia(crossMatched, nowMillis())
            store.writeMatch(
                MetadataMatchRecord(titleId, provider, crossMatched.externalId, null, manual = false, matchedAtMillis = nowMillis()),
            )
            recordCrossMatches(titleId, crossMatched)
            return RecordedResult.Found(crossMatched)
        }

        return RecordedResult.Unrecorded
    }

    /** Searches this provider live for a title with nothing on record yet. Three shots at most -
     * each is a request, and a title that has not turned up by then is very likely simply absent.
     * See searchQueriesFor for why the later ones are worth spending. */
    private suspend fun liveSearchMetadataFor(anime: AnimeTitle, provider: MetadataProviderId): ExternalMetadata? {
        val titleId = anime.id
        val label = anime.englishName?.takeIf(String::isNotBlank) ?: anime.originalName

        var searched = false
        var previousCandidateIds: Set<Int>? = null
        val queries = searchQueriesFor(anime, MAX_SEARCHES_PER_PROVIDER)
        if (queries.isEmpty()) {
            log("liveSearchMetadataFor: '$label' provider=$provider has no usable name to search with (englishName/originalName/synonyms all blank)")
        }
        for (query in queries) {
            // Null means the request itself failed - unreachable, rate-limited, or (as AniList was
            // while this was written) disabled outright. Give up on this provider and leave no
            // record: a remembered "no match" is a week-long statement about the *title*.
            val results = if (provider == MetadataProviderId.ANILIST) {
                anilist.searchCandidates(query)
                    ?.map { candidate -> ScoredEntry(candidate, ExternalMetadata(provider, candidate.externalId)) }
            } else {
                searchProvider(provider, query)
            }
            if (results == null) {
                log("liveSearchMetadataFor: '$label' provider=$provider search request failed for '$query' - leaving no record")
                return null
            }
            searched = true
            val best = pickBestMatch(anime, results.map(ScoredEntry::candidate))
            if (best == null) {
                log("liveSearchMetadataFor: '$label' provider=$provider search for '$query' returned ${results.size} candidate(s) - none cleared the match threshold")
                val candidateIds = results.mapTo(mutableSetOf()) { it.media.externalId }
                // A provider sometimes normalizes decorative punctuation and season suffixes before
                // searching. If two variants therefore produce the identical candidate set, a third
                // spelling is overwhelmingly another request for the same answer. Keep trying when
                // the second spelling actually changed the pool, because that is the useful case.
                if (previousCandidateIds == candidateIds && query != queries.last()) {
                    log("liveSearchMetadataFor: '$label' provider=$provider second spelling repeated the same candidates; skipping remaining variants")
                    break
                }
                previousCandidateIds = candidateIds
                continue
            }
            // AniList's lightweight search returns only match fields. Fetch the single chosen id
            // through its own batch lane; the other providers already returned full media.
            val found = if (provider == MetadataProviderId.ANILIST) {
                anilist.fetchById(best.externalId) ?: return null
            } else {
                results.firstOrNull { it.media.externalId == best.externalId }?.media ?: continue
            }
            log("liveSearchMetadataFor: '$label' provider=$provider search for '$query' matched externalId=${best.externalId} confidence=${(best.confidence * 100).toInt()}%")
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
            // Background card matching deliberately chooses whichever provider can answer first.
            // Keep that provider for details too: cross-provider bindings are useful fallbacks, but
            // must not make the poster change after the user taps the card they just saw.
            if (store.readDisplayProvider(titleId) == null) {
                store.writeDisplayProvider(titleId, provider)
            }
            return found
        }

        if (searched) {
            log("liveSearchMetadataFor: '$label' provider=$provider no match after ${queries.size} quer${if (queries.size == 1) "y" else "ies"}, recording no-match for ${TTL_NO_MATCH_MILLIS / (24L * 60 * 60 * 1_000)}d")
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

    /**
     * The order a live search tries providers in. A foreground lookup keeps the user's preference -
     * it is the title on screen. A background one (a page of cards) goes to whichever provider's
     * queue frees up first, ties keeping preference, so a page spreads by each provider's actual
     * capacity instead of evenly: Kitsu takes more, the slowest queue (Jikan's) takes less.
     */
    private suspend fun liveSearchOrder(order: List<MetadataProviderId>): List<MetadataProviderId> {
        if (currentCoroutineContext()[MetadataPriority]?.background != true) return order
        return order.sortedBy { provider ->
            when (provider) {
                MetadataProviderId.ANILIST -> anilist.estimatedWaitMillis()
                MetadataProviderId.MAL -> mal.estimatedWaitMillis()
                MetadataProviderId.KITSU -> kitsu.estimatedWaitMillis()
            }
        }
    }

    private fun refreshInBackground(titleId: String, provider: MetadataProviderId, externalId: Int) {
        val key = "${provider.id}:$externalId"
        if (!pendingRefreshes.add(key)) return
        refreshScope.launch {
            try {
                val refreshed = runCatching { fetchById(provider, externalId) }.getOrNull() ?: return@launch
                store.writeMedia(refreshed, nowMillis())
                recordCrossMatches(titleId, refreshed)
            } finally {
                pendingRefreshes.remove(key)
            }
        }
    }

    /** Refreshes a catalog entry without inventing a source-title binding just to do so. */
    private fun refreshEntryInBackground(provider: MetadataProviderId, externalId: Int) {
        val key = "${provider.id}:$externalId"
        if (!pendingRefreshes.add(key)) return
        refreshScope.launch {
            try {
                val refreshed = runCatching { fetchById(provider, externalId) }.getOrNull() ?: return@launch
                store.writeMedia(refreshed, nowMillis())
            } finally {
                pendingRefreshes.remove(key)
            }
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
