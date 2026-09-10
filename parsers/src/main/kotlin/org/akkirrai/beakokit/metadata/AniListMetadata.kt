package org.akkirrai.beakokit.metadata

import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.TitleRating
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AniList as a *metadata* provider: a source that declares `useExternalMetadata` in its manifest
 * keeps everything describing what is actually playable (episodes, playback groups, player links,
 * availableEpisodeCount) and hands the descriptive half of its [AnimeTitle] - name, description,
 * poster, genres, score, airing - to AniList instead.
 *
 * Everything in this file is pure on purpose: matching a source title to an AniList entry and
 * deciding which field wins are the two things worth testing, and neither needs a network or a
 * cache to answer. The GraphQL client and the store around it live in [AniListMetadataProvider].
 *
 * Mirrors the desktop client's `shared/anilistMetadata.ts` field for field, so both clients
 * describe the same title the same way.
 */
@Serializable
data class ExternalMetadata(
    val anilistId: Int,
    val malId: Int? = null,
    val romajiName: String? = null,
    val englishName: String? = null,
    val nativeName: String? = null,
    val synonyms: List<String> = emptyList(),
    val description: String? = null,
    val posterUrl: String? = null,
    /** Carried through the provider even though [mergeExternalMetadata] has nowhere to put it -
     * [AnimeTitle] has no banner field, and adding one is the details screen's job, not this
     * layer's. Fetching it now costs nothing (same GraphQL query) and saves a cache-wide refetch
     * when the screen grows a banner. */
    val bannerUrl: String? = null,
    val studios: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val type: String? = null,
    val status: String? = null,
    val episodeCount: Int? = null,
    /** AniList's own 0..100 scale, converted to this app's 0..10 only at the merge. */
    val averageScore: Int? = null,
    /** Unix seconds, matching [AnimeTitle.nextEpisodeAt] - AniList reports the same unit. */
    val nextEpisodeAt: Long? = null,
    val isAdult: Boolean = false,
)

/** The rating source name AniList's score is filed under in [AnimeTitle.ratings] - matches how
 * sources name theirs ("Shikimori", "MAL", ...), and is what lets the merge below replace only
 * *its own* previous entry instead of wiping the ratings a source collected. */
const val ANILIST_RATING_SOURCE = "AniList"

/** Below this a match is treated as no match at all. A prefix-only name hit that also contradicts
 * the year lands under it; anything with an exact name and no contradiction clears it. */
const val MATCH_CONFIDENCE_THRESHOLD = 0.6

private val FORMAT_TO_TYPE = mapOf(
    "TV" to "tv",
    "TV_SHORT" to "tv",
    "MOVIE" to "movie",
    "OVA" to "ova",
    "ONA" to "ona",
    "SPECIAL" to "special",
    "MUSIC" to "special",
)

// CANCELLED and HIATUS are deliberately absent: AnimeReleaseStatus has no equivalent, and an
// unmapped status falls back to the source's, which at least says something the app can render
// rather than being forced into a wrong one.
private val MEDIA_STATUS_TO_STATUS = mapOf(
    "RELEASING" to "ongoing",
    "FINISHED" to "released",
    "NOT_YET_RELEASED" to "announced",
)

fun mapAniListFormat(format: String?): String? = format?.let(FORMAT_TO_TYPE::get)

fun mapAniListStatus(status: String?): String? = status?.let(MEDIA_STATUS_TO_STATUS::get)

private val SPOILER_BLOCK = Regex("""<span[^>]*markdown_spoiler[^>]*>.*?</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val PARAGRAPH_END = Regex("""</p>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]+>""")
private val SOURCE_NOTE = Regex("""\n*\(Source:[^)]*\)\s*$""", RegexOption.IGNORE_CASE)
private val TRAILING_SPACE = Regex("""[ \t]+\n""")
private val BLANK_LINES = Regex("""\n{3,}""")

/**
 * AniList descriptions are HTML, not text: `<br>` line breaks, `<i>`, and - the reason this cannot
 * just be a tag strip - `<span class="markdown_spoiler">` blocks holding actual plot spoilers,
 * which have to *go*, not merely lose their tags.
 */
fun sanitizeAniListDescription(html: String?): String? {
    if (html.isNullOrBlank()) return null
    val text = html
        .replace(SPOILER_BLOCK, "")
        .replace(LINE_BREAK, "\n")
        .replace(PARAGRAPH_END, "\n\n")
        .replace(ANY_TAG, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        // Source-note tails ("(Source: MAL)") say nothing to a reader looking at a description
        // inside another app, and AniList puts one on a large share of entries.
        .replace(SOURCE_NOTE, "")
        .replace(TRAILING_SPACE, "\n")
        .replace(BLANK_LINES, "\n\n")
        .trim()
    return text.ifBlank { null }
}

private val ORDINAL_SEASON = Regex("""(\d+)(?:st|nd|rd|th)\s+season""")
private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

/** Comparison form for title matching: case, punctuation, and the ordinal season wording that
 * differs between every site ("2nd Season" vs "Season 2") all removed, so the only thing left to
 * differ is the words themselves. Latin, Cyrillic, kana and CJK all survive; everything else is
 * treated as a separator. */
fun normalizeTitleForMatch(value: String): String = value
    .lowercase()
    .replace("&", " and ")
    .replace(ORDINAL_SEASON) { match -> "season ${match.groupValues[1]}" }
    .replace(NON_ALPHANUMERIC, " ")
    .trim()

/** Every name a source title is known by, in normalized form - a match on any one of them counts,
 * since sources disagree wildly about which of the three is the "main" one.
 *
 * Deliberately not [AnimeTitle.russianName]: AniList holds no Russian titles, so matching against
 * one can only ever produce noise (and a Russian source's romaji name is already in the list). */
fun AnimeTitle.matchableNames(): List<String> = buildList {
    add(originalName)
    englishName?.let(::add)
    japaneseName?.let(::add)
    addAll(synonyms)
}.map(::normalizeTitleForMatch).filter(String::isNotBlank).distinct()

data class MatchCandidate(
    val anilistId: Int,
    val names: List<String>,
    val year: Int? = null,
    val type: String? = null,
    val format: String? = null,
)

/** How sure a match is, 0..1. Only names decide whether a candidate is *possible*; year and type
 * decide between the several candidates an exact-name search always returns for anything with a
 * sequel, a movie, and an OVA sharing one name. */
fun scoreCandidate(anime: AnimeTitle, candidate: MatchCandidate): Double {
    val wanted = anime.matchableNames()
    if (wanted.isEmpty()) return 0.0
    val offered = candidate.names.map(::normalizeTitleForMatch).filter(String::isNotBlank)
    if (offered.isEmpty()) return 0.0

    val nameScore = when {
        wanted.any(offered::contains) -> 1.0
        wanted.any { name -> offered.any { other -> other.startsWith(name) || name.startsWith(other) } } -> 0.7
        else -> return 0.0
    }

    val candidateType = candidate.type ?: mapAniListFormat(candidate.format)
    // A year that is one off is not evidence against a match: a late-season show airs in December
    // on one site and January on another, and the two disagree by a calendar year every time.
    val yearScore = when {
        anime.year == null || candidate.year == null -> 0.0
        abs(anime.year - candidate.year) <= 1 -> 1.0
        else -> -1.0
    }
    val typeScore = when {
        anime.type == null || candidateType == null -> 0.0
        anime.type.equals(candidateType, ignoreCase = true) -> 1.0
        else -> -1.0
    }

    // Rounded because these weights do not sum to exactly 1 in binary floating point, and a score
    // is also persisted as a percentage - two reasons for a perfect match to read as exactly 1.
    val score = ((nameScore * 0.7 + yearScore * 0.2 + typeScore * 0.1) * 1000).roundToInt() / 1000.0
    return score.coerceIn(0.0, 1.0)
}

data class MetadataMatch(val anilistId: Int, val confidence: Double)

fun pickBestMatch(anime: AnimeTitle, candidates: List<MatchCandidate>): MetadataMatch? = candidates
    .map { candidate -> MetadataMatch(candidate.anilistId, scoreCandidate(anime, candidate)) }
    .filter { match -> match.confidence >= MATCH_CONFIDENCE_THRESHOLD }
    .maxByOrNull(MetadataMatch::confidence)

/** Replaces this provider's own previous rating rather than appending - a title refetched five
 * times should not grow five AniList rows in its ratings list. */
private fun withAniListRating(existing: List<TitleRating>, averageScore: Int?): List<TitleRating> {
    if (averageScore == null) return existing
    val others = existing.filterNot { rating -> rating.source == ANILIST_RATING_SOURCE }
    // AniList scores out of 100, every source in this app scores out of 10, and the details screen
    // renders them all through one formatter.
    return listOf(TitleRating(source = ANILIST_RATING_SOURCE, value = averageScore / 10.0)) + others
}

private fun <T> preferExternal(external: T?, own: T?): T? = external ?: own

private fun preferExternal(external: List<String>, own: List<String>): List<String> =
    external.ifEmpty { own }

/**
 * The whole point of the feature, in one function: descriptive fields come from AniList, and
 * anything describing what is actually playable stays with the source.
 *
 * Field by field rather than object-level replacement, so a partially-filled AniList entry (they
 * exist - an announced title has no episode count and no score) degrades to the source's value for
 * that one field instead of blanking it.
 *
 * Not replaced, on purpose:
 * - `id`, [AnimeTitle.availableEpisodeCount], and everything playback-related: the source is the
 *   only thing that knows what it has actually uploaded.
 * - [AnimeTitle.russianName]: AniList has no Russian titles, and dropping the source's would make
 *   a Russian source's screen worse, not better.
 * - [AnimeTitle.ageRating]: AniList has no equivalent field (only an adult flag), so there is
 *   nothing to replace it with.
 * - [AnimeTitle.relatedAnime]/[AnimeTitle.franchiseAnime]/[AnimeTitle.similarAnime]: their ids
 *   address *this source's* catalog, and AniList ids would make every one of those cards a dead
 *   link. Replacing them needs a search-by-name jump that does not exist yet.
 */
fun mergeExternalMetadata(anime: AnimeTitle, external: ExternalMetadata?): AnimeTitle {
    if (external == null) return anime
    return anime.copy(
        originalName = external.romajiName?.takeIf(String::isNotBlank)
            ?: external.nativeName?.takeIf(String::isNotBlank)
            ?: anime.originalName,
        englishName = preferExternal(external.englishName, anime.englishName),
        japaneseName = preferExternal(external.nativeName, anime.japaneseName),
        synonyms = preferExternal(external.synonyms, anime.synonyms),
        description = preferExternal(external.description, anime.description),
        posterUrl = preferExternal(external.posterUrl, anime.posterUrl),
        genres = preferExternal(external.genres, anime.genres),
        studios = preferExternal(external.studios, anime.studios),
        year = preferExternal(external.year, anime.year),
        type = preferExternal(external.type, anime.type),
        status = preferExternal(external.status, anime.status),
        episodeCount = preferExternal(external.episodeCount, anime.episodeCount),
        nextEpisodeAt = preferExternal(external.nextEpisodeAt, anime.nextEpisodeAt),
        ratings = withAniListRating(anime.ratings, external.averageScore),
    )
}
