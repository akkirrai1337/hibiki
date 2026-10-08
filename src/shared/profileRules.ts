import type { DailyActivity, LibraryEntry } from "./types";

/**
 * How a test vector's inputs turn into what the rules actually take.
 *
 * Shared by the generator (scripts/generateProfileVectors.ts) and the test that checks against it,
 * so the two can't disagree about what a vector means - the vectors would still pass while
 * describing something else. The Android port needs the same two mappings, and they are small and
 * exact on purpose: a vector says "a library entry in this category with these genres" and "this
 * day had activity", nothing more.
 */

/** The only two fields of a library entry the profile rules read. */
export interface VectorEntry {
  category: string;
  genres: string[];
}

export function vectorEntriesToLibrary(entries: VectorEntry[]): LibraryEntry[] {
  return entries.map((entry, index) => ({
    sourceId: "vector",
    animeId: String(index),
    category: entry.category,
    anime: { id: String(index), sourceId: "vector", genres: entry.genres },
  })) as unknown as LibraryEntry[];
}

/** 1 = a day with activity, 0 = without. Oldest first; the last entry is today, which the streak
 * rules treat specially (an unfinished day never breaks a run). */
export function vectorDaysToSeries(days: number[]): DailyActivity[] {
  return days.map((active, index) => ({
    date: `2026-01-${String(index + 1).padStart(2, "0")}`,
    watchedMs: active ? 60_000 : 0,
    completedCount: 0,
  })) as unknown as DailyActivity[];
}
