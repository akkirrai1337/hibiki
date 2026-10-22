import type { DailyActivity, LibraryEntry, StatsEpisode, TitleRatingEntry } from "@shared/types";

// The profile's "watch style": a handful of 0..1 axes for the radar, and the plain numbers they are
// read from. Everything here comes from what the app itself recorded - episode progress, the daily
// tally, the library and ratings - and what the sources said about a title (genres, status, length).
// A title watched but never added to the library has no stored metadata, so it counts towards the
// totals but not the genres.

export type StyleAxisId = "binge" | "consistency" | "completion" | "variety" | "night" | "ongoing";

export interface StyleAxis {
  id: StyleAxisId;
  /** 0..1, where the radar draws it. */
  value: number;
  /** The measure behind it, before scaling: episodes per day, a share, a count of genres. */
  raw: number;
}

export interface GenreShare {
  name: string;
  /** Of all genre weight, 0..1. */
  share: number;
}

export interface WatchStats {
  axes: StyleAxis[];
  totalWatchedMs: number;
  episodesWatched: number;
  activeDays: number;
  titlesStarted: number;
  titlesFinished: number;
  titlesDropped: number;
  episodesPerActiveDay: number;
  minutesPerActiveDay: number;
  bestDayEpisodes: number;
  longestStreak: number;
  /** The hour with the most episodes, or null with none. */
  peakHour: number | null;
  genres: GenreShare[];
  topDub: { name: string; episodes: number } | null;
  ratingAverage: number | null;
  ratingCount: number;
}

// Where an axis is full. Picked so a keen but ordinary viewer lands somewhere in the middle, not
// pinned to the edge: six episodes on a day you watch, twenty active days out of the last thirty...
const BINGE_FULL = 6;
const CONSISTENCY_WINDOW_DAYS = 30;
const CONSISTENCY_FULL = 20;
const VARIETY_FULL_GENRES = 12;
const NIGHT_FULL = 0.5;
const ONGOING_FULL = 0.6;
// A title still being watched isn't dropped yet: it leaves the completion axis alone until it has
// gone this long untouched.
const IN_PROGRESS_MS = 14 * 86_400_000;
// "Night" is 22:00 to 04:59.
const isNightHour = (hour: number) => hour >= 22 || hour < 5;

const clamp01 = (n: number) => (Number.isFinite(n) ? Math.min(1, Math.max(0, n)) : 0);
const titleKey = (sourceId: string, animeId: string) => `${sourceId}\u0000${animeId}`;

function dayOf(key: string): Date {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function dateKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** The longest run of consecutive calendar days with any activity. */
function longestRun(activeKeys: string[]): number {
  const days = [...new Set(activeKeys)].map((k) => dayOf(k).getTime()).sort((a, b) => a - b);
  let best = 0;
  let run = 0;
  for (let i = 0; i < days.length; i++) {
    // Rounded: a day across a daylight-saving change is 23 or 25 hours long.
    run = i > 0 && Math.round((days[i] - days[i - 1]) / 86_400_000) === 1 ? run + 1 : 1;
    best = Math.max(best, run);
  }
  return best;
}

/** How evenly weight is spread across names: 1 when equal, 0 when it is all one. */
function evenness(weights: number[]): number {
  const total = weights.reduce((s, w) => s + w, 0);
  if (weights.length < 2 || total <= 0) return 0;
  const entropy = weights.reduce((s, w) => (w > 0 ? s - (w / total) * Math.log(w / total) : s), 0);
  return entropy / Math.log(weights.length);
}

export function computeWatchStats(input: {
  library: LibraryEntry[];
  episodes: StatsEpisode[];
  ratings: TitleRatingEntry[];
  activity: DailyActivity[];
  now?: number;
}): WatchStats {
  const now = input.now ?? Date.now();
  const watched = input.episodes.filter((e) => e.watched);
  const libraryByKey = new Map(input.library.map((e) => [titleKey(e.sourceId, e.animeId), e]));

  // --- Days ---------------------------------------------------------------------------------------
  const activeRows = input.activity.filter((d) => d.watchedMs > 0 || d.completedCount > 0);
  const totalWatchedMs = input.activity.reduce((s, d) => s + d.watchedMs, 0);
  const tallied = input.activity.reduce((s, d) => s + d.completedCount, 0);
  // The daily tally only began counting at some point; progress rows may know of more.
  const episodesWatched = Math.max(tallied, watched.length);
  const activeDays = activeRows.length;
  const episodesPerActiveDay = activeDays ? tallied / activeDays : 0;
  const minutesPerActiveDay = activeDays ? totalWatchedMs / activeDays / 60_000 : 0;
  const bestDayEpisodes = activeRows.reduce((m, d) => Math.max(m, d.completedCount), 0);
  const longestStreak = longestRun(activeRows.map((d) => d.date));

  const today = new Date(now);
  const windowStart = dateKey(new Date(today.getFullYear(), today.getMonth(), today.getDate() - (CONSISTENCY_WINDOW_DAYS - 1)));
  const recentActiveDays = activeRows.filter((d) => d.date >= windowStart).length;

  // --- Hours --------------------------------------------------------------------------------------
  // Watched episodes by the local hour they were last played in.
  const byHour = Array<number>(24).fill(0);
  for (const e of watched) byHour[new Date(e.updatedAt).getHours()]++;
  const nightEpisodes = byHour.reduce((s, n, h) => (isNightHour(h) ? s + n : s), 0);
  const nightShare = watched.length ? nightEpisodes / watched.length : 0;
  const peak = byHour.reduce((best, n, h) => (n > byHour[best] ? h : best), 0);
  const peakHour = byHour[peak] > 0 ? peak : null;

  // --- Titles -------------------------------------------------------------------------------------
  const perTitle = new Map<string, { watched: number; lastAt: number }>();
  for (const e of input.episodes) {
    const key = titleKey(e.sourceId, e.titleId);
    const t = perTitle.get(key) ?? { watched: 0, lastAt: 0 };
    if (e.watched) t.watched++;
    t.lastAt = Math.max(t.lastAt, e.updatedAt);
    perTitle.set(key, t);
  }

  const startedKeys = new Set<string>();
  for (const [key, t] of perTitle) if (t.watched > 0) startedKeys.add(key);
  for (const e of input.library) if (e.category === "completed" || e.category === "dropped") startedKeys.add(titleKey(e.sourceId, e.animeId));

  let titlesFinished = 0;
  let titlesDropped = 0;
  let settled = 0;
  for (const key of startedKeys) {
    const entry = libraryByKey.get(key);
    const t = perTitle.get(key) ?? { watched: 0, lastAt: 0 };
    const total = entry?.anime.episodeCount ?? null;
    const available = entry?.anime.availableEpisodeCount ?? null;
    const finished =
      entry?.category === "completed" ||
      (total != null && total > 0 && t.watched >= total) ||
      // An ongoing caught up with: as finished as it can be for now.
      (entry?.anime.status === "ongoing" && available != null && available > 0 && t.watched >= available);
    if (finished) {
      titlesFinished++;
      settled++;
    } else if (entry?.category === "dropped") {
      titlesDropped++;
      settled++;
    } else if (now - t.lastAt > IN_PROGRESS_MS) {
      // Left unfinished and untouched: it counts against completion, without being called dropped.
      settled++;
    }
  }
  const completion = settled ? titlesFinished / settled : 0;

  // Weighted by episodes watched, so a genre is what was actually watched rather than collected; a
  // title marked completed without episodes here (watched elsewhere) weighs one.
  const genreWeight = new Map<string, { name: string; weight: number }>();
  let ongoingEpisodes = 0;
  let knownStatusEpisodes = 0;
  for (const entry of input.library) {
    const key = titleKey(entry.sourceId, entry.animeId);
    const count = perTitle.get(key)?.watched ?? 0;
    const weight = count > 0 ? count : entry.category === "completed" ? 1 : 0;
    if (weight === 0) continue;
    for (const raw of new Set((entry.anime.genres ?? []).map((g) => g.trim()).filter(Boolean))) {
      const id = raw.toLocaleLowerCase();
      const g = genreWeight.get(id) ?? { name: raw.charAt(0).toLocaleUpperCase() + raw.slice(1), weight: 0 };
      g.weight += weight;
      genreWeight.set(id, g);
    }
    if (entry.anime.status && count > 0) {
      knownStatusEpisodes += count;
      if (entry.anime.status === "ongoing") ongoingEpisodes += count;
    }
  }
  const genreTotal = [...genreWeight.values()].reduce((s, g) => s + g.weight, 0);
  const genres = [...genreWeight.values()]
    .sort((a, b) => b.weight - a.weight || a.name.localeCompare(b.name))
    .map((g) => ({ name: g.name, share: genreTotal ? g.weight / genreTotal : 0 }));
  const variety = evenness([...genreWeight.values()].map((g) => g.weight)) * clamp01(genreWeight.size / VARIETY_FULL_GENRES);
  const ongoingShare = knownStatusEpisodes ? ongoingEpisodes / knownStatusEpisodes : 0;

  // --- Dubs and ratings ---------------------------------------------------------------------------
  const dubs = new Map<string, number>();
  for (const e of watched) {
    const name = e.translation?.trim();
    if (name) dubs.set(name, (dubs.get(name) ?? 0) + 1);
  }
  const topDubEntry = [...dubs].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0];

  const ratingCount = input.ratings.length;
  const ratingAverage = ratingCount ? input.ratings.reduce((s, r) => s + r.rating, 0) / ratingCount : null;

  return {
    axes: [
      { id: "binge", value: clamp01(episodesPerActiveDay / BINGE_FULL), raw: episodesPerActiveDay },
      { id: "consistency", value: clamp01(recentActiveDays / CONSISTENCY_FULL), raw: recentActiveDays },
      { id: "completion", value: clamp01(completion), raw: completion },
      { id: "variety", value: clamp01(variety), raw: genreWeight.size },
      { id: "night", value: clamp01(nightShare / NIGHT_FULL), raw: nightShare },
      { id: "ongoing", value: clamp01(ongoingShare / ONGOING_FULL), raw: ongoingShare },
    ],
    totalWatchedMs,
    episodesWatched,
    activeDays,
    titlesStarted: startedKeys.size,
    titlesFinished,
    titlesDropped,
    episodesPerActiveDay,
    minutesPerActiveDay,
    bestDayEpisodes,
    longestStreak,
    peakHour,
    genres,
    topDub: topDubEntry ? { name: topDubEntry[0], episodes: topDubEntry[1] } : null,
    ratingAverage,
    ratingCount,
  };
}
