// Recommendations from one source - gathering the inputs for scoring.ts, all from that source and
// this machine: what was watched (watch_progress), each watched title's details (mostly already in
// cached_anime from when its page was opened), the titles those details call similar, and a couple of
// genre searches. Nothing from outside the source: its catalog is the only one that can be played.
import { eq, max, sql } from "drizzle-orm";
import { genreFilterFor } from "@shared/genreLink";
import type { AnimeTitle, SourceRecommendations } from "@shared/types";
import { library, watchProgress } from "../db/schema";
import type { ExtensionRuntime } from "../extensions/runtime";
import { logger } from "../logger";
import { cacheAnime, getCachedAnimeMany } from "../offlineCache";
import { getPlatform } from "../platform";
import { findContinuations, genreProfile, MIN_SEEDS, MIN_WATCHED_EPISODES, rankCandidates, relatedToTitle, topGenres, type Candidate, type Seed } from "./scoring";

/** The most recently watched titles taken as seeds; older taste fades out this way. */
const MAX_SEEDS = 20;
/** Seed details fetched from the source when not cached - each is a request, so only a few. */
const MAX_DETAIL_FETCHES = 6;
const GENRE_SEARCHES = 2;
const GENRE_SEARCH_SIZE = 24;
const PICK_COUNT = 18;
const CONTINUATION_COUNT = 12;
/** A result stays good this long, unless what was watched changes. */
const CACHE_MS = 6 * 60 * 60 * 1000;

const cache = new Map<string, { key: string; at: number; result: Promise<SourceRecommendations> }>();

export function recommendationsForSource(runtime: ExtensionRuntime, sourceId: string, sort?: string): Promise<SourceRecommendations> {
  return (async () => {
    const watched = await watchedTitles(sourceId);
    // What was watched is the whole input: the same history gives the same answer, so a result is
    // reused until it changes (or ages out - a source's catalog does move).
    const key = `${sort ?? ""}|${watched.map((title) => `${title.titleId}:${title.episodes}`).join(",")}`;
    const hit = cache.get(sourceId);
    if (hit && hit.key === key && Date.now() - hit.at < CACHE_MS) return hit.result;
    const result = compute(runtime, sourceId, sort, watched);
    cache.set(sourceId, { key, at: Date.now(), result });
    result.catch(() => cache.delete(sourceId));
    return result;
  })();
}

interface WatchedTitle {
  titleId: string;
  /** Episodes marked watched. */
  episodes: number;
  /** Any progress at all, watched or not. */
  lastAt: number;
}

async function watchedTitles(sourceId: string): Promise<WatchedTitle[]> {
  const rows = await getPlatform().db.get()
    .select({
      titleId: watchProgress.titleId,
      episodes: sql<number>`sum(case when ${watchProgress.watched} then 1 else 0 end)`,
      lastAt: max(watchProgress.updatedAt),
    })
    .from(watchProgress)
    .where(eq(watchProgress.sourceId, sourceId))
    .groupBy(watchProgress.titleId)
    .all();
  return rows
    .map((row) => ({ titleId: row.titleId, episodes: Number(row.episodes) || 0, lastAt: Number(row.lastAt) || 0 }))
    .sort((a, b) => b.lastAt - a.lastAt);
}

async function compute(runtime: ExtensionRuntime, sourceId: string, sort: string | undefined, watched: WatchedTitle[]): Promise<SourceRecommendations> {
  const startedAt = Date.now();
  const seedTitles = watched.filter((title) => title.episodes >= MIN_WATCHED_EPISODES).slice(0, MAX_SEEDS);
  const empty: SourceRecommendations = { seedCount: seedTitles.length, neededSeeds: MIN_SEEDS, picks: [], continuations: [] };
  if (seedTitles.length === 0) return empty;

  const seeds = await seedDetails(runtime, sourceId, seedTitles);
  // Anything started, and anything already in the library, is not news to the person.
  const exclude = new Set<string>(watched.map((title) => title.titleId));
  const listed = await getPlatform().db.get().select({ animeId: library.animeId }).from(library).where(eq(library.sourceId, sourceId)).all();
  for (const row of listed) exclude.add(row.animeId);

  const continuations = findContinuations(seeds, exclude, CONTINUATION_COUNT);
  if (seeds.length < MIN_SEEDS) return { ...empty, seedCount: seeds.length, continuations };

  // A franchise's own entries are continuations, not recommendations.
  const recommendExclude = new Set(exclude);
  for (const seed of seeds) {
    for (const entry of [...(seed.anime.relatedAnime ?? []), ...(seed.anime.franchiseAnime ?? [])]) recommendExclude.add(entry.id);
  }

  const candidates = new Map<string, Candidate>();
  const add = (anime: AnimeTitle, similarTo?: string) => {
    const existing = candidates.get(anime.id);
    if (existing) {
      if (similarTo) existing.similarTo.add(similarTo);
      // A search card usually knows more (genres) than a "similar" entry.
      if (!existing.anime.genres?.length && anime.genres?.length) existing.anime = { ...existing.anime, ...anime };
      return;
    }
    candidates.set(anime.id, { anime, similarTo: new Set(similarTo ? [similarTo] : []) });
  };
  for (const seed of seeds) {
    for (const entry of seed.anime.similarAnime ?? []) add(relatedToTitle(entry, sourceId), seed.anime.id);
  }
  // The genres "similar" entries lack, from whatever of them is already cached.
  const cached = await getCachedAnimeMany([...candidates.keys()].map((animeId) => ({ sourceId, animeId })));
  for (const [id, candidate] of candidates) {
    const details = cached[`${sourceId}:${id}`]?.title;
    if (details) candidate.anime = { ...candidate.anime, ...details };
  }

  for (const anime of await genreSearches(runtime, sourceId, sort, seeds)) add(anime);

  const picks = rankCandidates(seeds, [...candidates.values()], recommendExclude, PICK_COUNT);
  logger.info(
    "recommend",
    `${sourceId}: ${picks.length} pick(s), ${continuations.length} continuation(s) from ${seeds.length} seed(s) and ${candidates.size} candidate(s) in ${Date.now() - startedAt}ms`,
  );
  return { seedCount: seeds.length, neededSeeds: MIN_SEEDS, picks, continuations };
}

/** Each seed's full details: from the cache, or - for a few - from the source. */
async function seedDetails(runtime: ExtensionRuntime, sourceId: string, titles: WatchedTitle[]): Promise<Seed[]> {
  const cached = await getCachedAnimeMany(titles.map((title) => ({ sourceId, animeId: title.titleId })));
  const missing = titles.filter((title) => !cached[`${sourceId}:${title.titleId}`]).slice(0, MAX_DETAIL_FETCHES);
  const fetched = new Map<string, AnimeTitle>();
  await Promise.all(missing.map(async (title) => {
    try {
      const anime = await runtime.getById(sourceId, title.titleId);
      fetched.set(title.titleId, anime);
      await cacheAnime(sourceId, title.titleId, anime);
    } catch (error) {
      logger.debug("recommend", `${sourceId}/${title.titleId}: no details (${error instanceof Error ? error.message : String(error)})`);
    }
  }));
  return titles.flatMap((title): Seed[] => {
    const anime = cached[`${sourceId}:${title.titleId}`]?.title ?? fetched.get(title.titleId);
    return anime ? [{ anime, watchedEpisodes: title.episodes }] : [];
  });
}

/** The source's own catalog for the seeds' strongest genres, in the order the home screen uses. */
async function genreSearches(runtime: ExtensionRuntime, sourceId: string, sort: string | undefined, seeds: Seed[]): Promise<AnimeTitle[]> {
  const genres = topGenres(genreProfile(seeds), GENRE_SEARCHES);
  if (genres.length === 0) return [];
  let catalog;
  try {
    catalog = await runtime.getFilterCatalog(sourceId);
  } catch {
    return [];
  }
  const results = await Promise.all(genres.map(async (genre) => {
    const filter = genreFilterFor(catalog, genre);
    if (!filter) return [];
    try {
      return await runtime.search(sourceId, { limit: GENRE_SEARCH_SIZE, sort, filters: { [filter.filterId]: filter.value } });
    } catch (error) {
      logger.debug("recommend", `${sourceId}: genre search "${genre}" failed: ${error instanceof Error ? error.message : String(error)}`);
      return [];
    }
  }));
  return results.flat();
}
