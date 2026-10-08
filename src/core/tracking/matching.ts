// Which tracker entry is a title of a source, and the other way round. Pure, so it is tested without
// a network. The comparison itself came back from the removed metadata feature (git 4a0023b), where
// it had earned its rules against real catalogs; what is new is how sure it has to be before it acts.
//
// A link here writes to someone's account - a wrong one puts the wrong show on their AniList list -
// so a match is only taken on its own when it is near certain AND clearly ahead of the next one.
// Anything less is left for the person to pick, which the title page offers.
import type { AnimeTitle, AnimeType } from "@shared/types";

const ROMAN_SEASON_NUMERALS: Record<string, string> = {
  ii: "2", iii: "3", iv: "4", v: "5", vi: "6", vii: "7", viii: "8", ix: "9", x: "10",
};

/** Comparison form for title matching: case, punctuation, the ordinal season wording that differs
 * between every site ("2nd Season" vs "Season 2"), and season numerals all normalized away, so the
 * only thing left to differ is the words themselves. Latin, Cyrillic, kana and CJK all survive;
 * everything else is treated as a separator. */
export function normalizeTitleForMatch(value: string): string {
  return value
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(/[^\p{Letter}\p{Number}]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ")
    .replace(/(\d+)(?:st|nd|rd|th) season/g, "season $1")
    .split(" ")
    // Only ever a *trailing* numeral, and never the whole title: "X" is a show in its own right,
    // and reading it as a tenth season would match it against anything.
    .map((word, index, words) => (index > 0 && index === words.length - 1 ? (ROMAN_SEASON_NUMERALS[word] ?? word) : word))
    .join(" ")
    .replace(/ season (\d+)$/, " $1");
}

// Tags a source appends for its own catalog - a dub/uncensored marker, a disambiguating format -
// which no tracker has ever heard of and which stop its search from finding the show at all.
const SOURCE_TAGS = /\s*[([][^)\]]*[)\]]\s*$/;
const TRAILING_SEASON = /\s+(?:season\s+\d+|\d+(?:st|nd|rd|th)\s+season|part\s+\d+)$/i;

/** The names a source title is known by on a tracker. Not russianName: no tracker holds Russian
 * titles, so matching against one only ever produces noise. */
export function trackerNamesOf(anime: Pick<AnimeTitle, "englishName" | "originalName" | "synonyms">): string[] {
  return [anime.englishName, anime.originalName, ...(anime.synonyms ?? [])].filter(
    (name): name is string => typeof name === "string" && name.trim().length > 0,
  );
}

/**
 * The queries to try against a text search, best first.
 *
 * A search matches text, not titles: "Jujutsu Kaisen (TV)" finds a New Year's special and
 * "Re:ZERO -Starting Life in Another World- Season 3" finds an unrelated show, because a site's own
 * decorations are searched for as if they were part of the name. Each variant strips one layer of
 * those - a trailing tag, then a season suffix. Scoring still decides what is accepted, so a broader
 * query only widens the pool it chooses from.
 */
export function searchQueriesFor(names: string[], limit = 3): string[] {
  const queries: string[] = [];
  for (const name of names) {
    const plain = name.replace(SOURCE_TAGS, "").replace(/[-–—_]+/g, " ").replace(/\s+/g, " ");
    for (const variant of [name, plain, plain.replace(TRAILING_SEASON, "")]) {
      const trimmed = variant.trim();
      // Compared as written: two spellings that normalize alike are still two questions to a search.
      if (trimmed.length > 0 && !queries.some((existing) => existing.toLowerCase() === trimmed.toLowerCase())) {
        queries.push(trimmed);
      }
    }
  }
  return queries.slice(0, limit);
}

/**
 * A normalized title split into the show and which season of it this is - so "Classroom of the
 * Elite IV" and "Classroom of the Elite 4th Season: Second Year, First Semester" meet, while a
 * sequel still stays apart from its own first season.
 */
function titleParts(normalized: string): { show: string; season: number | null } {
  const seasonMatch = /^(.*?) season (\d+)(?: .*)?$/.exec(normalized);
  if (seasonMatch) return { show: seasonMatch[1].trim(), season: Number(seasonMatch[2]) };
  const trailingNumber = /^(.*?) (\d{1,2})$/.exec(normalized);
  if (trailingNumber) return { show: trailingNumber[1].trim(), season: Number(trailingNumber[2]) };
  return { show: normalized, season: null };
}

/** One side of a comparison: the names a record is known by (as written), and the two facts that
 * separate a sequel, a movie and an OVA sharing one name. */
export interface Comparable {
  names: string[];
  year?: number | null;
  type?: AnimeType | null;
}

/**
 * How sure a match between two records is, 0..1. Direction-free: the same rules decide "which
 * tracker entry is this title" and "which title of the source is this entry".
 *
 * Names decide whether a match is possible at all: one shared name 1, the same show and season
 * worded differently 0.95, a prefix only 0.7 (which is also what a first season looks like next to
 * its sequel). Then a year within one counts for it and a further one against it, and so does the
 * type.
 */
export function scoreMatch(wanted: Comparable, offered: Comparable): number {
  const wantedNames = [...new Set(wanted.names.map(normalizeTitleForMatch).filter(Boolean))];
  const offeredNames = [...new Set(offered.names.map(normalizeTitleForMatch).filter(Boolean))];
  if (wantedNames.length === 0 || offeredNames.length === 0) return 0;

  const offeredParts = offeredNames.map(titleParts);
  let nameScore: number;
  if (wantedNames.some((name) => offeredNames.includes(name))) nameScore = 1;
  else if (
    wantedNames
      .map(titleParts)
      .some((part) => offeredParts.some((other) => other.show === part.show && other.season === part.season))
  ) {
    nameScore = 0.95;
  } else if (wantedNames.some((name) => offeredNames.some((other) => other.startsWith(name) || name.startsWith(other)))) {
    nameScore = 0.7;
  } else return 0;

  // One year apart is not evidence against: a show starting in late December is filed under either.
  const yearScore = wanted.year == null || offered.year == null ? 0 : Math.abs(wanted.year - offered.year) <= 1 ? 1 : -1;
  const typeScore = wanted.type == null || offered.type == null ? 0 : wanted.type === offered.type ? 1 : -1;
  const score = Math.round((nameScore * 0.7 + yearScore * 0.2 + typeScore * 0.1) * 1000) / 1000;
  return Math.max(0, Math.min(1, score));
}

/** Taken without asking only from here up: a name (or the same show and season, worded differently)
 * and the year agreeing, with the type not disagreeing. A name without a year - at most 0.8 with the
 * type, and 0.7 alone, the same as a sequel's - never is. */
export const CONFIDENT_MATCH = 0.86;
/** And only this far ahead of the best *other* candidate: two seasons of one name airing in one year
 * both score at the top, and picking either would be a coin toss written to an account. */
export const CONFIDENT_MARGIN = 0.05;

/**
 * The one candidate that clearly is `wanted`, or null. Candidates sharing a key (the same entry
 * found by two queries) count once.
 */
export function pickConfident<K>(wanted: Comparable, candidates: Array<{ key: K; comparable: Comparable }>): { key: K; score: number } | null {
  const best = new Map<K, number>();
  for (const candidate of candidates) {
    const score = scoreMatch(wanted, candidate.comparable);
    if (score > (best.get(candidate.key) ?? -1)) best.set(candidate.key, score);
  }
  const ranked = [...best.entries()].sort((a, b) => b[1] - a[1]);
  const [top, runnerUp] = ranked;
  if (!top || top[1] < CONFIDENT_MATCH) return null;
  if (runnerUp && top[1] - runnerUp[1] < CONFIDENT_MARGIN) return null;
  return { key: top[0], score: top[1] };
}

const ANILIST_FORMAT_TO_TYPE: Record<string, AnimeType> = {
  TV: "tv",
  TV_SHORT: "tv",
  MOVIE: "movie",
  OVA: "ova",
  ONA: "ona",
  SPECIAL: "special",
  MUSIC: "special",
};

export function anilistFormatToType(format: string | null | undefined): AnimeType | null {
  return format ? (ANILIST_FORMAT_TO_TYPE[format] ?? null) : null;
}
