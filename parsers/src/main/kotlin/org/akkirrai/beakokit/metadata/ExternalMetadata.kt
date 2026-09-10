package org.akkirrai.beakokit.metadata

import kotlinx.serialization.Serializable
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.TitleRating
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * External metadata: describing a source's titles from a metadata aggregator instead of from the
 * source's own pages. A source that admits its own metadata is the weaker half of what it returns
 * declares `useExternalMetadata` in its manifest, and the app fills in name, description, poster,
 * genres, score and airing from AniList, MAL or Kitsu.
 *
 * This file is the provider-neutral core - the shape all three normalize into, how a source title
 * is matched to an entry, and which fields that entry is then allowed to replace. Each provider's
 * own response mapping is its own file, and the network and cache live beside them. Everything here
 * is pure, because matching rules and merge rules are the two things worth testing and neither
 * needs a network to answer.
 *
 * Mirrors the desktop client's `shared/externalMetadata.ts` rule for rule, so the two clients
 * describe the same title the same way.
 */
@Serializable
enum class MetadataProviderId {
    ANILIST,
    MAL,
    KITSU;

    /** How this provider's score is labelled in [AnimeTitle.ratings] - the same shape a source uses
     * for its own ("Shikimori", "MAL", ...). */
    val ratingSource: String
        get() = when (this) {
            ANILIST -> "AniList"
            MAL -> "MAL"
            KITSU -> "Kitsu"
        }

    /** The wire name, shared with the desktop client and with anything stored on disk. */
    val id: String
        get() = name.lowercase()

    companion object {
        fun fromId(id: String?): MetadataProviderId? = entries.firstOrNull { it.id == id?.lowercase() }
    }
}

// Every label this layer may have written before. The merge clears all of them rather than only the
// provider it is writing now: switching provider must not leave yesterday's AniList score sitting
// next to today's MAL one, as if the source had reported both.
private val PROVIDER_RATING_SOURCES = MetadataProviderId.entries.map(MetadataProviderId::ratingSource)

/** One title as described by one provider, normalized at the provider edge so nothing downstream
 * has to know about `coverImage.extraLarge` or `images.jpg.large_image_url`. */
@Serializable
data class ExternalMetadata(
    val provider: MetadataProviderId,
    /** This provider's own id for the title. */
    val externalId: Int,
    /** The *other* providers' ids, when this one publishes them. What lets a switch between
     * providers, or a fallback to one, reuse a match that is already established instead of
     * searching by name again - which matters because search is the fragile, rate-limited half of
     * every one of these APIs. */
    val anilistId: Int? = null,
    val malId: Int? = null,
    val kitsuId: Int? = null,
    val romajiName: String? = null,
    val englishName: String? = null,
    val nativeName: String? = null,
    val synonyms: List<String> = emptyList(),
    val description: String? = null,
    val posterUrl: String? = null,
    /** Carried even though [mergeExternalMetadata] has nowhere to put it - [AnimeTitle] has no
     * banner field, and adding one is the details screen's job, not this layer's. */
    val bannerUrl: String? = null,
    val studios: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val type: String? = null,
    val status: String? = null,
    val episodeCount: Int? = null,
    /** Out of 10, converted at the provider edge - AniList and Kitsu score out of 100, MAL out of
     * 10, and the details screen renders every rating through one formatter. */
    val score: Double? = null,
    val scoreVotes: Int? = null,
    /** MAL and Kitsu publish one; AniList has no equivalent field at all. */
    val ageRating: String? = null,
    /** Unix seconds, matching [AnimeTitle.nextEpisodeAt]. MAL publishes no such timestamp, so a
     * title described from MAL keeps whatever countdown its source reported. */
    val nextEpisodeAt: Long? = null,
    val isAdult: Boolean = false,
)

/** The user's half of the decision (the source's half is its manifest's `useExternalMetadata`). */
data class ExternalMetadataPreferences(
    val enabled: Boolean = true,
    /** Per-source answers that win over [enabled] in both directions, keyed by source id. */
    val overrides: Map<String, Boolean> = emptyMap(),
    val provider: MetadataProviderId = MetadataProviderId.ANILIST,
    /** Whether the other providers are tried when the preferred one has nothing or cannot be
     * reached. Both APIs have outages, and a screen that quietly falls back still looks right. */
    val fallbackEnabled: Boolean = true,
)

/**
 * Which providers to try, in order, for a given source.
 *
 * Empty means "describe this title from its source alone": either the source never asked for
 * external metadata, or the user turned it off.
 */
fun metadataProviderOrder(
    preferences: ExternalMetadataPreferences,
    sourceId: String,
    sourceDeclaresIt: Boolean,
): List<MetadataProviderId> {
    if (!sourceDeclaresIt) return emptyList()
    if (!(preferences.overrides[sourceId] ?: preferences.enabled)) return emptyList()
    if (!preferences.fallbackEnabled) return listOf(preferences.provider)
    return listOf(preferences.provider) + MetadataProviderId.entries.filterNot { it == preferences.provider }
}

/** What an aggregator-driven catalog asks for. Deliberately small: these are the three shapes a
 * catalog screen actually offers, not a general query language over three different APIs. */
data class ExternalCatalogRequest(
    val mode: Mode,
    val offset: Int = 0,
    val limit: Int = 24,
    /** For [Mode.SEASON] - defaults to the season now when absent. */
    val season: AnimeSeason? = null,
    val seasonYear: Int? = null,
) {
    enum class Mode { TRENDING, POPULAR, SEASON }
}

enum class AnimeSeason { WINTER, SPRING, SUMMER, FALL;

    val id: String get() = name.lowercase()
}

/** The season a date falls in, by the convention every aggregator uses: January to March is winter,
 * and December belongs to the winter that January continues. */
fun seasonNow(now: java.time.LocalDate = java.time.LocalDate.now()): Pair<AnimeSeason, Int> {
    val season = when (now.monthValue) {
        1, 2, 12 -> AnimeSeason.WINTER
        3, 4, 5 -> AnimeSeason.SPRING
        6, 7, 8 -> AnimeSeason.SUMMER
        else -> AnimeSeason.FALL
    }
    return season to if (now.monthValue == 12) now.year + 1 else now.year
}

/** Which providers can be browsed rather than only looked up. MAL through Jikan has nothing worth
 * calling a trending endpoint, so it stays a description provider. */
val CATALOG_PROVIDERS = listOf(MetadataProviderId.KITSU, MetadataProviderId.ANILIST)

/**
 * Whether an aggregator-driven catalog has anything to ask, given the providers allowed for a
 * source.
 *
 * Not the same question as "may this source be described": MAL can describe a title but cannot be
 * browsed, so preferring it with the fallback turned off leaves an order that describes fine and
 * browses not at all. A screen that asks the wrong one of these turns itself on and then reports
 * that no provider answered.
 */
fun canBrowseProviders(order: List<MetadataProviderId>): Boolean = order.any { it in CATALOG_PROVIDERS }

/**
 * Which title of a source is this provider entry - the reverse of the matcher, for a catalog browsed
 * from the aggregator and resolved to a source only when a title is opened.
 *
 * Harder than the forward direction, and worth knowing why: a source's *search results* carry a name
 * and often nothing else, while the entry being resolved has a year and a type. So most of these are
 * decided by the name alone, which is exactly the case that cannot tell a sequel from its first
 * season - hence the same threshold, and hence a manual pick has to stay part of the normal flow
 * rather than an error path.
 */
fun pickSourceTitleFor(entry: ExternalMetadata, titles: List<AnimeTitle>): Pair<String, Double>? {
    val wanted = ComparableTitle(entry.matchableNames(), entry.year, entry.type)
    var best: Pair<String, Double>? = null
    for (title in titles) {
        val confidence = scoreNames(wanted, ComparableTitle(title.matchableNames(), title.year, title.type))
        if (confidence >= MATCH_CONFIDENCE_THRESHOLD && (best == null || confidence > best!!.second)) {
            best = title.id to confidence
        }
    }
    return best
}

/** The queries to try against a *source's* search for a provider entry, best first. A source indexes
 * what it publishes, which is usually the romaji name and sometimes the English one. */
fun sourceSearchQueriesFor(entry: ExternalMetadata, limit: Int = 2): List<String> =
    listOfNotNull(entry.romajiName, entry.englishName, entry.nativeName)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .take(limit)

/** One provider's entry, as identified by the user rather than by the matcher. Kitsu's own web URLs
 * name a title by slug rather than by id, so a reference carries one or the other. */
data class MetadataReference(
    val provider: MetadataProviderId,
    val externalId: Int? = null,
    val slug: String? = null,
)

private val ANILIST_LINK = Regex("""anilist\.co/(?:anime|manga)/(\d+)""", RegexOption.IGNORE_CASE)
private val MAL_LINK = Regex("""myanimelist\.net/anime/(\d+)""", RegexOption.IGNORE_CASE)
private val KITSU_LINK = Regex("""kitsu\.(?:io|app)/anime/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
private val BARE_ID = Regex("""^\d+$""")

/**
 * Reads a provider entry out of whatever was pasted into the manual-rebind box: an AniList, MAL or
 * Kitsu page URL, or a bare id belonging to [defaultProvider].
 *
 * A URL carries the provider with it, which is the point - pasting the page you are looking at is
 * the one way to fix a wrong match that works even while a provider's *search* is down, which is
 * exactly the state AniList's was in when this was written.
 */
fun parseMetadataReference(input: String, defaultProvider: MetadataProviderId): MetadataReference? {
    val text = input.trim()
    if (text.isEmpty()) return null
    ANILIST_LINK.find(text)?.let { return MetadataReference(MetadataProviderId.ANILIST, it.groupValues[1].toIntOrNull()) }
    MAL_LINK.find(text)?.let { return MetadataReference(MetadataProviderId.MAL, it.groupValues[1].toIntOrNull()) }
    KITSU_LINK.find(text)?.let { match ->
        val id = match.groupValues[1]
        return if (BARE_ID.matches(id)) {
            MetadataReference(MetadataProviderId.KITSU, id.toIntOrNull())
        } else {
            MetadataReference(MetadataProviderId.KITSU, slug = id)
        }
    }
    // A bare number is an id for whichever provider is currently in charge - the ids are unrelated
    // between them, so guessing another would bind the title to a different show entirely.
    if (BARE_ID.matches(text)) return MetadataReference(defaultProvider, text.toIntOrNull())
    return null
}

/** Where to send someone who wants to look at the entry a title is bound to. */
fun metadataEntryUrl(provider: MetadataProviderId, externalId: Int): String = when (provider) {
    MetadataProviderId.ANILIST -> "https://anilist.co/anime/$externalId"
    MetadataProviderId.MAL -> "https://myanimelist.net/anime/$externalId"
    MetadataProviderId.KITSU -> "https://kitsu.app/anime/$externalId"
}

/** Below this a match is treated as no match at all. A prefix-only name hit that also contradicts
 * the year lands under it; anything with an exact name and no contradiction clears it. */
const val MATCH_CONFIDENCE_THRESHOLD = 0.6

private val SPOILER_BLOCK = Regex("""<span[^>]*markdown_spoiler[^>]*>.*?</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val PARAGRAPH_END = Regex("""</p>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]+>""")
private val SOURCE_NOTE = Regex("""\n*\(Source:[^)]*\)\s*$""", RegexOption.IGNORE_CASE)
private val WRITER_NOTE = Regex("""\n*\[Written by[^\]]*\]\s*$""", RegexOption.IGNORE_CASE)
private val TRAILING_SPACE = Regex("""[ \t]+\n""")
private val BLANK_LINES = Regex("""\n{3,}""")

/**
 * Turns a provider's HTML or marked-up synopsis into plain text.
 *
 * All three need this, for different reasons: AniList descriptions are HTML, including
 * `<span class="markdown_spoiler">` blocks holding actual plot spoilers, which have to *go* rather
 * than merely lose their tags; MAL and Kitsu synopses are plain text but carry an attribution tail.
 */
fun sanitizeDescription(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw
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
        .replace(SOURCE_NOTE, "")
        .replace(WRITER_NOTE, "")
        .replace(TRAILING_SPACE, "\n")
        .replace(BLANK_LINES, "\n\n")
        .trim()
    return text.ifBlank { null }
}

// A season named in Roman numerals on one site and in digits on the other is the single most common
// way two records of the same show fail to look alike ("Classroom of the Elite IV" against
// "Classroom of the Elite 4th Season"). Only the values a season plausibly takes - a stray "i" or
// "x" in a real title would otherwise become a number.
private val ROMAN_SEASON_NUMERALS = mapOf(
    "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6",
    "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
)

private val ORDINAL_SEASON = Regex("""(\d+)(?:st|nd|rd|th) season""")
private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")
private val TRAILING_SEASON_NUMBER = Regex(""" season (\d+)$""")

/** Comparison form for title matching: case, punctuation, the ordinal season wording that differs
 * between every site ("2nd Season" vs "Season 2"), and season numerals all normalized away, so the
 * only thing left to differ is the words themselves. Latin, Cyrillic, kana and CJK all survive;
 * everything else is treated as a separator. */
fun normalizeTitleForMatch(value: String): String {
    val collapsed = value
        .lowercase()
        .replace("&", " and ")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(ORDINAL_SEASON) { match -> "season ${match.groupValues[1]}" }
    val words = collapsed.split(" ")
    // Only ever a *trailing* numeral, and never the whole title: "X" is a show in its own right, and
    // reading it as a tenth season would match it against anything.
    val withDigits = words.mapIndexed { index, word ->
        if (index > 0 && index == words.lastIndex) ROMAN_SEASON_NUMERALS[word] ?: word else word
    }.joinToString(" ")
    return withDigits.replace(TRAILING_SEASON_NUMBER) { match -> " ${match.groupValues[1]}" }
}

// Tags a source appends for its own catalog - a dub/uncensored marker, a disambiguating format -
// which no aggregator has ever heard of and which stop their search from finding the show at all.
private val SOURCE_TAGS = Regex("""\s*[(\[][^)\]]*[)\]]\s*$""")
private val TRAILING_SEASON = Regex("""\s+(?:season\s+\d+|\d+(?:st|nd|rd|th)\s+season|part\s+\d+)$""", RegexOption.IGNORE_CASE)
private val BRACKET_DASHES = Regex("""[-–—_]+""")
private val REPEATED_SPACE = Regex("""\s+""")

/**
 * The queries to try against a provider's search, best first.
 *
 * A provider's search matches text, not titles: "Jujutsu Kaisen (TV)" finds a New Year's special and
 * "Re:ZERO -Starting Life in Another World- Season 3" finds an unrelated show, because the source's
 * own decorations are being searched for as if they were part of the name. Each variant strips one
 * layer of those. Scoring still decides what is accepted, so a broader query only widens the pool it
 * chooses from.
 */
fun searchQueriesFor(anime: AnimeTitle, limit: Int = 3): List<String> {
    val names = (listOf(anime.englishName, anime.originalName) + anime.synonyms)
        .filterNotNull()
        .filter(String::isNotBlank)
    val queries = mutableListOf<String>()
    for (name in names) {
        // Dashes used as brackets break a text search outright: Kitsu answers that one with an
        // unrelated show, and with the dashes flattened it answers with the right one. Stripped
        // before the season suffix is, because the punctuation is the more common blocker.
        val plain = name.replace(SOURCE_TAGS, "").replace(BRACKET_DASHES, " ").replace(REPEATED_SPACE, " ")
        for (variant in listOf(name, plain, plain.replace(TRAILING_SEASON, ""))) {
            val trimmed = variant.trim()
            // Compared as written, not in matching form: the whole point of a variant is that a
            // provider's text search treats two spellings of one name differently.
            if (trimmed.isNotEmpty() && queries.none { it.equals(trimmed, ignoreCase = true) }) {
                queries += trimmed
            }
        }
    }
    return queries.take(limit)
}

/** Every name a source title is known by, in normalized form - a match on any one of them counts,
 * since sources disagree wildly about which of the three is the "main" one.
 *
 * Deliberately not [AnimeTitle.russianName]: no provider holds Russian titles, so matching against
 * one can only ever produce noise (and a Russian source's romaji name is already in the list). */
fun AnimeTitle.matchableNames(): List<String> = buildList {
    add(originalName)
    englishName?.let(::add)
    japaneseName?.let(::add)
    addAll(synonyms)
}.map(::normalizeTitleForMatch).filter(String::isNotBlank).distinct()

/** Every name a provider entry is known by, normalized - the aggregator's side of a comparison. */
fun ExternalMetadata.matchableNames(): List<String> =
    (listOf(romajiName, englishName, nativeName) + synonyms)
        .filterNotNull()
        .map(::normalizeTitleForMatch)
        .filter(String::isNotBlank)
        .distinct()

private val SEASON_IN_TITLE = Regex("""^(.*?) season (\d+)(?: .*)?$""")
private val TRAILING_NUMBER = Regex("""^(.*?) (\d{1,2})$""")

/**
 * A normalized title split into the show and which season of it this is.
 *
 * The two records of one season rarely spell it the same way: a source writes "Classroom of the
 * Elite IV" and the aggregator files it as "Classroom of the Elite 4th Season: Second Year, First
 * Semester". Reading both as (show, season) lets those meet, while still keeping a sequel apart from
 * its own first season - which a plain prefix comparison cannot do.
 */
private fun titleParts(normalized: String): Pair<String, Int?> {
    SEASON_IN_TITLE.find(normalized)?.let { match ->
        return match.groupValues[1].trim() to match.groupValues[2].toIntOrNull()
    }
    TRAILING_NUMBER.find(normalized)?.let { match ->
        return match.groupValues[1].trim() to match.groupValues[2].toIntOrNull()
    }
    return normalized to null
}

/** One side of a comparison: every name a record is known by, already normalized, plus the two facts
 * that separate a sequel from its own first season. */
data class ComparableTitle(
    val names: List<String>,
    val year: Int? = null,
    val type: String? = null,
)

/**
 * How sure a match between two records is, 0..1 - the whole comparison, and the only place it
 * happens.
 *
 * Direction-free on purpose: the same rules decide "which provider entry is this source title" and
 * "which title of this source is this provider entry", so the two can never drift into disagreeing
 * about the same pair.
 */
fun scoreNames(wanted: ComparableTitle, offered: ComparableTitle): Double {
    val wantedNames = wanted.names.filter(String::isNotBlank)
    val offeredNames = offered.names.filter(String::isNotBlank)
    if (wantedNames.isEmpty() || offeredNames.isEmpty()) return 0.0

    val offeredParts = offeredNames.map(::titleParts)
    val nameScore = when {
        wantedNames.any(offeredNames::contains) -> 1.0
        // Same show, same season, different wording around it.
        wantedNames.map(::titleParts).any { part -> offeredParts.any { it == part } } -> 0.95
        // A prefix and nothing more - which is also what a first season looks like next to its
        // sequel, so this never clears the threshold on its own.
        wantedNames.any { name -> offeredNames.any { it.startsWith(name) || name.startsWith(it) } } -> 0.7
        else -> return 0.0
    }

    // A year that is one off is not evidence against a match: a late-season show airs in December on
    // one site and January on another, and the two disagree by a calendar year every time.
    val yearScore = when {
        wanted.year == null || offered.year == null -> 0.0
        abs(wanted.year - offered.year) <= 1 -> 1.0
        else -> -1.0
    }
    val typeScore = when {
        wanted.type == null || offered.type == null -> 0.0
        wanted.type.equals(offered.type, ignoreCase = true) -> 1.0
        else -> -1.0
    }

    // Rounded because these weights do not sum to exactly 1 in binary floating point, and a score is
    // also persisted as a percentage - two reasons for a perfect match to read as exactly 1.
    val score = ((nameScore * 0.7 + yearScore * 0.2 + typeScore * 0.1) * 1000).roundToInt() / 1000.0
    return score.coerceIn(0.0, 1.0)
}

data class MatchCandidate(
    val externalId: Int,
    val names: List<String>,
    val year: Int? = null,
    val type: String? = null,
)

data class MetadataMatch(val externalId: Int, val confidence: Double)

fun scoreCandidate(anime: AnimeTitle, candidate: MatchCandidate): Double = scoreNames(
    ComparableTitle(anime.matchableNames(), anime.year, anime.type),
    ComparableTitle(candidate.names.map(::normalizeTitleForMatch), candidate.year, candidate.type),
)

fun pickBestMatch(anime: AnimeTitle, candidates: List<MatchCandidate>): MetadataMatch? = candidates
    .map { candidate -> MetadataMatch(candidate.externalId, scoreCandidate(anime, candidate)) }
    .filter { it.confidence >= MATCH_CONFIDENCE_THRESHOLD }
    .maxByOrNull(MetadataMatch::confidence)

/** Replaces whatever this layer wrote before rather than appending - a title refetched five times,
 * or described from another provider after a switch, should not grow five rating rows. */
private fun withProviderRating(existing: List<TitleRating>, external: ExternalMetadata): List<TitleRating> {
    val others = existing.filterNot { it.source in PROVIDER_RATING_SOURCES }
    val score = external.score ?: return existing
    return listOf(TitleRating(source = external.provider.ratingSource, value = score, votes = external.scoreVotes)) + others
}

private fun <T> preferExternal(external: T?, own: T?): T? = external ?: own

private fun preferExternal(external: List<String>, own: List<String>): List<String> = external.ifEmpty { own }

/**
 * The whole point of the feature, in one function: descriptive fields come from the provider, and
 * anything describing what is actually playable stays with the source.
 *
 * Field by field rather than object-level replacement, so a partially-filled entry (they exist - an
 * announced title has no episode count and no score, and MAL publishes no airing timestamp at all)
 * degrades to the source's value for that one field instead of blanking it.
 *
 * Not replaced, on purpose:
 * - `id`, [AnimeTitle.availableEpisodeCount], and everything playback-related: the source is the
 *   only thing that knows what it has actually uploaded.
 * - [AnimeTitle.russianName]: no provider has Russian titles, and dropping the source's would make a
 *   Russian source's screen worse, not better.
 * - [AnimeTitle.relatedAnime]/[AnimeTitle.franchiseAnime]/[AnimeTitle.similarAnime]: their ids
 *   address *this source's* catalog, and a provider's ids would make every one of those cards a dead
 *   link.
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
        ageRating = preferExternal(external.ageRating, anime.ageRating),
        nextEpisodeAt = preferExternal(external.nextEpisodeAt, anime.nextEpisodeAt),
        ratings = withProviderRating(anime.ratings, external),
    )
}
