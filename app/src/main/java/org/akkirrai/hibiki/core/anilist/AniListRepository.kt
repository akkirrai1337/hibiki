package org.akkirrai.hibiki.core.anilist

import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.hibiki.catalog.model.AniListEnrichment
import org.akkirrai.hibiki.catalog.model.AniListMatchHints
import org.akkirrai.hibiki.catalog.model.Anime

/** Enriches a source-provided [Anime] with AniList data it doesn't have (banner, score,
 *  characters, directors) -- poster/title/episodes always stay from the source. Best-effort:
 *  any failure or no-match degrades to null, never throws, never blocks the details screen. */
class AniListRepository internal constructor(
    private val cache: AniListCacheRepository,
    private val client: AniListGraphQlClient,
    private val matcher: TitleMatcher,
) {
    constructor(cache: AniListCacheRepository) : this(
        cache = cache,
        client = AniListGraphQlClient(),
        matcher = TitleMatcher(),
    )

    suspend fun enrich(anime: Anime): AniListEnrichment? {
        val hints = anime.aniListMatchHints ?: return null

        cache.get(anime.id)?.let { return it.toEnrichmentOrNull() }

        return runCatching {
            val candidates = client.search(hints.searchQuery(fallback = anime.title))
            val match = AniListMatcher.bestMatch(matcher, hints, candidates)
            if (match == null) {
                cache.put(anime.id, AniListCacheEntry(anilistId = null, resolvedAtMillis = System.currentTimeMillis()))
                return null
            }
            val detail = client.getById(match.id) ?: return null
            val enrichment = detail.toEnrichment(match.id)
            cache.put(anime.id, enrichment.toCacheEntry())
            enrichment
        }.getOrNull()
    }
}

/** AniList's search index barely understands Cyrillic queries -- many RU sources only populate
 *  [AniListMatchHints.russianName] and stuff the actual English/romaji title into synonyms
 *  (e.g. YummyAnime leaves englishName/originalName null and puts "Spirited Away"/"Sen to Chihiro
 *  no Kamikakushi" in other_titles -> synonyms). Prefer the first non-Cyrillic candidate name;
 *  only fall back to a Cyrillic one (or [fallback]) if nothing else is available. */
internal fun AniListMatchHints.searchQuery(fallback: String): String {
    val candidates = (listOfNotNull(englishName, originalName) + synonyms)
        .filter { it.isNotBlank() }
    return candidates.firstOrNull { !it.hasCyrillic() }
        ?: candidates.firstOrNull()
        ?: fallback
}

private fun String.hasCyrillic(): Boolean = any { it in 'Ѐ'..'ӿ' }
