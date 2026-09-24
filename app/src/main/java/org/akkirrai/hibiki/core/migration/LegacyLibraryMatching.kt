package org.akkirrai.hibiki.core.migration

import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.beakokit.matching.TitleMatcher
import org.akkirrai.beakokit.model.AnimeTitle
import org.json.JSONObject

/**
 * The sources of the retired scripted (JS) extension repository, by the id they wrote into stored
 * titles, with the name they were shown under. A library entry keyed `source:anichi:<id>` belongs
 * to one of these; nothing that runs now uses those ids.
 */
internal val LEGACY_JS_SOURCES: Map<String, String> = mapOf(
    "ani-liberty" to "AniLiberty",
    "anichi" to "Anichi",
    "anikappa" to "AniKappa",
    "anikoto" to "AniKoto",
    "animego" to "AnimeGo",
    "animelib" to "AnimeLib",
    "animepahe" to "AnimePahe",
    "animevost" to "AnimeVost",
    "anitube" to "AniTube",
    "anixart" to "Anixart",
    "donghuastream" to "DonghuaStream",
    "hentaimama" to "HentaiMama",
    "kickassanime" to "KickAssAnime",
    "mikai" to "Mikai",
    "yummy-anime" to "YummyAnime",
)

/** The pure decisions of the library transfer - kept apart from storage and network so they can be tested. */
internal object LegacyLibraryMatching {
    /** Confidence needed before a found title replaces a saved one; below it the entry is left alone. */
    const val MATCH_THRESHOLD = 0.75

    private val matcher = TitleMatcher()
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")

    /** The legacy source a stored title id belongs to, or null when it is not a retired source's. */
    fun legacySourceIdOf(titleId: String): String? {
        val trimmed = titleId.trim()
        // Titles from the very first YummyAnime-only builds were bare numeric ids.
        if (trimmed.isNotEmpty() && trimmed.all(Char::isDigit)) return "yummy-anime"
        val key = AnimeKey.parse(trimmed) ?: return null
        return key.sourceId.value.takeIf { it in LEGACY_JS_SOURCES }
    }

    /** Whether two source names are the same source, ignoring case and punctuation ("Ani-Liberty" = "AniLiberty"). */
    fun sameSourceName(a: String, b: String): Boolean = squash(a) == squash(b)

    /** The installed source that best stands in for [legacyName], by name; null when none does. */
    fun <T> suggestTarget(legacyName: String, installed: List<T>, nameOf: (T) -> String): T? =
        installed.firstOrNull { sameSourceName(nameOf(it), legacyName) }
            ?: installed.firstOrNull { squash(nameOf(it)).startsWith(squash(legacyName)) }

    /** What a saved entry says about its title, as something the matcher can score candidates against. */
    fun probeFor(animeJson: JSONObject): AnimeTitle {
        val alternatives = animeJson.optJSONArray("alternativeTitles")
            ?.let { array -> (0 until array.length()).map(array::optString) }
            .orEmpty()
        val year = (animeJson.optString("releaseDate").ifBlank { animeJson.optString("subtitle") })
            .let(YEAR::find)?.value?.toIntOrNull()
        return AnimeTitle(
            id = animeJson.optString("id"),
            originalName = animeJson.optString("title"),
            synonyms = alternatives,
            year = year,
        )
    }

    /** The search terms to try for [probe], best first: its own name, then its alternative names. */
    fun queriesFor(probe: AnimeTitle): List<String> =
        (listOf(probe.originalName) + probe.synonyms).map(String::trim).filter(String::isNotBlank).distinct().take(3)

    /** The candidate that is confidently the same title as [probe], or null. */
    fun bestMatch(probe: AnimeTitle, candidates: List<AnimeTitle>): AnimeTitle? =
        candidates
            .map { candidate ->
                candidate to matcher.confidence(
                    title = probe,
                    candidateNames = candidate.allNames(),
                    candidateYear = candidate.year,
                    candidateType = candidate.type,
                    candidateEpisodes = candidate.episodeCount,
                )
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MATCH_THRESHOLD }
            ?.first

    /** [animeJson] re-pointed at [match]: same saved fields, the new source's id, and its poster when it has one. */
    fun rewritten(animeJson: JSONObject, match: AnimeTitle): JSONObject =
        JSONObject(animeJson.toString()).apply {
            put("id", match.id)
            match.posterUrl?.takeIf(String::isNotBlank)?.let { put("posterUrl", it) }
        }

    private fun squash(name: String): String = name.lowercase().filter(Char::isLetterOrDigit)
}
