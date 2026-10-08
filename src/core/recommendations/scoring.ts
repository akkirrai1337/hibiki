// Recommendations from one source, for what was watched on that source - the pure part: who counts
// as a seed, what the seeds have in common, and how a candidate ranks against them. No fetching here
// (see forSource.ts), so the rules can be tested against made-up titles.
//
// The only signal is episodes watched: a title with MIN_WATCHED_EPISODES or more is one the person
// liked enough to keep going. Guessing more from status or drop-off was judged not worth its noise.
// Everything else comes from the source itself - its "similar" lists, which are the site's own
// recommendations, and its genre names, which mean the same thing everywhere within one source.
import type { AnimeTitle, RecommendationReason, RecommendedTitle, RelatedAnimeTitle } from "@shared/types";

/** Episodes watched before a title counts as liked. */
export const MIN_WATCHED_EPISODES = 3;
/** Seeds needed before recommending anything: one title is a coincidence, three are a taste. */
export const MIN_SEEDS = 3;

export interface Seed {
  anime: AnimeTitle;
  watchedEpisodes: number;
}

/** A title some seed points at, or one a genre search found. */
export interface Candidate {
  anime: AnimeTitle;
  /** Seeds whose "similar" list holds this title, by id. */
  similarTo: Set<string>;
}

/** A related/similar entry as a title card can show it. */
export function relatedToTitle(entry: RelatedAnimeTitle, sourceId: string): AnimeTitle {
  return {
    id: entry.id,
    sourceId,
    russianName: entry.title,
    posterUrl: entry.posterUrl ?? null,
    type: entry.type ?? null,
    year: entry.year ?? null,
    episodeCount: entry.episodeCount ?? null,
    status: entry.status ?? null,
  };
}

const nameOf = (anime: AnimeTitle) => anime.russianName || anime.englishName || anime.originalName || anime.id;
const genreKey = (genre: string) => genre.trim().toLowerCase();

/** How often each genre appears across the seeds, 0-1 (1 = in every seed), keyed by lower-cased name. */
export function genreProfile(seeds: Seed[]): Map<string, { name: string; weight: number }> {
  const counts = new Map<string, { name: string; count: number }>();
  for (const seed of seeds) {
    for (const genre of new Set((seed.anime.genres ?? []).map((g) => g.trim()).filter(Boolean))) {
      const key = genreKey(genre);
      const entry = counts.get(key) ?? { name: genre, count: 0 };
      entry.count += 1;
      counts.set(key, entry);
    }
  }
  const profile = new Map<string, { name: string; weight: number }>();
  for (const [key, { name, count }] of counts) profile.set(key, { name, weight: count / Math.max(1, seeds.length) });
  return profile;
}

/** The profile's strongest genres, most shared first. */
export function topGenres(profile: Map<string, { name: string; weight: number }>, count: number): string[] {
  return [...profile.values()].sort((a, b) => b.weight - a.weight).slice(0, count).map((entry) => entry.name);
}

/** Cosine between the candidate's genres (each weight 1) and the profile. null when the candidate lists none. */
function genreMatch(genres: string[] | undefined, profile: Map<string, { name: string; weight: number }>): number | null {
  const keys = [...new Set((genres ?? []).map(genreKey).filter(Boolean))];
  if (keys.length === 0) return null;
  let dot = 0;
  let profileNorm = 0;
  for (const { weight } of profile.values()) profileNorm += weight * weight;
  for (const key of keys) dot += profile.get(key)?.weight ?? 0;
  if (dot === 0 || profileNorm === 0) return 0;
  return dot / (Math.sqrt(keys.length) * Math.sqrt(profileNorm));
}

/** Share of seeds of the candidate's type (TV, movie...); 0.5 when it is not known. */
function typeMatch(type: AnimeTitle["type"], seeds: Seed[]): number {
  if (!type) return 0.5;
  const known = seeds.filter((seed) => seed.anime.type);
  if (known.length === 0) return 0.5;
  return known.filter((seed) => seed.anime.type === type).length / known.length;
}

/**
 * The candidates in order, best first, each with why it is there.
 *
 * Being in a seed's "similar" list is worth most - the site itself made that link, and two seeds
 * agreeing on a title is as strong as it gets. Genres decide among the rest, and between equally
 * recommended titles; the title's type breaks what is left. A title whose genres are unknown (a
 * "similar" entry carries none) is scored as an average fit rather than a poor one.
 */
export function rankCandidates(seeds: Seed[], candidates: Candidate[], exclude: Set<string>, limit: number): RecommendedTitle[] {
  const profile = genreProfile(seeds);
  const seedName = new Map(seeds.map((seed) => [seed.anime.id, nameOf(seed.anime)]));
  // Most recently watched first, as seeds arrive - the "similar to" names read best that way.
  const seedOrder = new Map(seeds.map((seed, index) => [seed.anime.id, index]));

  const scored = candidates
    .filter((candidate) => !exclude.has(candidate.anime.id))
    .map((candidate) => {
      const support = Math.min(1, candidate.similarTo.size / 2);
      const genres = genreMatch(candidate.anime.genres, profile);
      const score = support * 0.5 + (genres ?? 0.4) * 0.4 + typeMatch(candidate.anime.type, seeds) * 0.1;
      return { candidate, score };
    })
    .sort((a, b) => b.score - a.score);

  return scored.slice(0, limit).map(({ candidate }): RecommendedTitle => {
    let reason: RecommendationReason;
    if (candidate.similarTo.size > 0) {
      const to = [...candidate.similarTo]
        .sort((a, b) => (seedOrder.get(a) ?? 0) - (seedOrder.get(b) ?? 0))
        .map((id) => seedName.get(id) ?? id);
      reason = { kind: "similar", to };
    } else {
      const own = new Set((candidate.anime.genres ?? []).map(genreKey));
      reason = { kind: "genres", genres: topGenres(profile, 6).filter((genre) => own.has(genreKey(genre))).slice(0, 2) };
    }
    return { anime: candidate.anime, reason };
  });
}

/**
 * What comes after a seed: its related titles from later years - the next season, a sequel film.
 * Kept apart from recommendations: it is not a suggestion so much as the obvious next thing.
 */
export function findContinuations(seeds: Seed[], exclude: Set<string>, limit: number): RecommendedTitle[] {
  const seen = new Set<string>();
  const out: RecommendedTitle[] = [];
  for (const seed of seeds) {
    const year = seed.anime.year;
    if (!year) continue;
    for (const entry of [...(seed.anime.relatedAnime ?? []), ...(seed.anime.franchiseAnime ?? [])]) {
      if (!entry.year || entry.year <= year || exclude.has(entry.id) || seen.has(entry.id)) continue;
      seen.add(entry.id);
      out.push({ anime: relatedToTitle(entry, seed.anime.sourceId), reason: { kind: "continues", of: nameOf(seed.anime) } });
    }
  }
  return out.slice(0, limit);
}
