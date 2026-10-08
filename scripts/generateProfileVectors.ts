/**
 * Regenerates src/shared/profileRules.vectors.json from the real implementation.
 *
 * The point is a single, language-neutral statement of what the profile rules do, that both this
 * app and the Android one can be tested against. XP, levels, achievement tiers and streaks are
 * about to exist twice - once in TypeScript here, once in Kotlin there - and two implementations
 * of the same arithmetic drift silently: the same watch history quietly becomes level 7 on the
 * desktop and level 6 on the phone, with nothing to point at.
 *
 * Generated rather than hand-written, so the expectations cannot be wrong about what this code
 * currently does. Reviewing the diff of this file is how a deliberate rule change gets noticed;
 * profileRules.test.ts fails if the code moves without it.
 *
 * Usage: npx tsx scripts/generateProfileVectors.ts
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { DailyActivity, LibraryEntry } from "../src/shared/types";
import { computeAchievements } from "../src/renderer/src/lib/achievements";
import { computeLevelProgress, totalXpEarned } from "../src/renderer/src/lib/levelProgress";
import { computeStreaks } from "../src/renderer/src/components/StreakBadge";

const OUTPUT = path.join(path.dirname(fileURLToPath(import.meta.url)), "../src/shared/profileRules.vectors.json");

const HOUR = 3_600_000;

/** The only two fields of a library entry the rules actually read. */
interface VectorEntry {
  category: string;
  genres: string[];
}

interface VectorCase {
  name: string;
  input: { entries: VectorEntry[]; lifetimeWatchedMs: number; bestStreak: number };
}

function entries(count: number, category: string, genres: string[] = []): VectorEntry[] {
  return Array.from({ length: count }, () => ({ category, genres }));
}

/** Distinct genres spread across `count` entries, for the genre-variety family. */
function withGenres(count: number, genreCount: number): VectorEntry[] {
  return Array.from({ length: count }, (_, i) => ({
    category: "planned",
    genres: [`genre-${i % genreCount}`],
  }));
}

// Boundaries, not round numbers: every case sits exactly on a tier threshold or one short of it,
// because off-by-one on a threshold is the drift most likely to survive a casual reading of both
// implementations.
const CASES: VectorCase[] = [
  { name: "empty profile", input: { entries: [], lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "one title, nothing watched", input: { entries: entries(1, "planned"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "collector tier 1 exactly", input: { entries: entries(10, "planned"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "collector one short of tier 2", input: { entries: entries(24, "planned"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "collector maxed", input: { entries: entries(50, "planned"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "collector past max", input: { entries: entries(120, "planned"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "finisher tier 1", input: { entries: entries(1, "completed"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "finisher tier 2", input: { entries: entries(10, "completed"), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "streak 2 - one short", input: { entries: [], lifetimeWatchedMs: 0, bestStreak: 2 } },
  { name: "streak 3 exactly", input: { entries: [], lifetimeWatchedMs: 0, bestStreak: 3 } },
  { name: "streak 7", input: { entries: [], lifetimeWatchedMs: 0, bestStreak: 7 } },
  { name: "streak 100 maxed", input: { entries: [], lifetimeWatchedMs: 0, bestStreak: 100 } },
  // watchedHoursFrom rounds to one decimal, so just under 3h must not clear the 3h tier.
  { name: "watch 2.9h", input: { entries: [], lifetimeWatchedMs: Math.round(2.9 * HOUR), bestStreak: 0 } },
  { name: "watch exactly 3h", input: { entries: [], lifetimeWatchedMs: 3 * HOUR, bestStreak: 0 } },
  { name: "watch exactly 24h", input: { entries: [], lifetimeWatchedMs: 24 * HOUR, bestStreak: 0 } },
  { name: "watch 100h", input: { entries: [], lifetimeWatchedMs: 100 * HOUR, bestStreak: 0 } },
  { name: "watch 500h maxed", input: { entries: [], lifetimeWatchedMs: 500 * HOUR, bestStreak: 0 } },
  // Episode-equivalents floor at 20 real minutes each, so this pins the flooring too.
  { name: "episodes just under 10", input: { entries: [], lifetimeWatchedMs: 9 * 20 * 60_000 + 59_000, bestStreak: 0 } },
  { name: "episodes exactly 10", input: { entries: [], lifetimeWatchedMs: 10 * 20 * 60_000, bestStreak: 0 } },
  { name: "episodes exactly 50", input: { entries: [], lifetimeWatchedMs: 50 * 20 * 60_000, bestStreak: 0 } },
  { name: "genres 10 distinct", input: { entries: withGenres(20, 10), lifetimeWatchedMs: 0, bestStreak: 0 } },
  { name: "genres 30 distinct", input: { entries: withGenres(40, 30), lifetimeWatchedMs: 0, bestStreak: 0 } },
  {
    name: "everything maxed",
    input: { entries: [...entries(50, "completed", ["a"]), ...withGenres(30, 30)], lifetimeWatchedMs: 500 * HOUR, bestStreak: 100 },
  },
];

/** Streak input as active/inactive days, oldest first, last entry being today. Language-neutral,
 * and it captures the two rules that are easy to get wrong: one missed day is forgiven, and today
 * being inactive never breaks a run because it hasn't finished yet. */
const STREAK_CASES: Array<{ name: string; days: number[] }> = [
  { name: "no activity", days: [0, 0, 0, 0, 0] },
  { name: "unbroken run ending today", days: [1, 1, 1, 1, 1] },
  { name: "today inactive - run holds, at risk", days: [1, 1, 1, 1, 0] },
  { name: "one gap is forgiven", days: [1, 1, 0, 1, 1] },
  { name: "two gaps in a row break it", days: [1, 1, 1, 0, 0, 1] },
  { name: "grace is repaid after use", days: [1, 0, 1, 0, 1, 1] },
  { name: "best is remembered after a break", days: [1, 1, 1, 1, 0, 0, 1] },
  { name: "single active day", days: [0, 0, 1] },
];

function toLibraryEntries(vector: VectorEntry[]): LibraryEntry[] {
  return vector.map((entry, index) => ({
    sourceId: "vector",
    animeId: String(index),
    category: entry.category,
    anime: { id: String(index), sourceId: "vector", genres: entry.genres },
  })) as unknown as LibraryEntry[];
}

function toSeries(days: number[]): DailyActivity[] {
  return days.map((active, index) => ({
    date: `2026-01-${String(index + 1).padStart(2, "0")}`,
    watchedMs: active ? 60_000 : 0,
    completedCount: 0,
  })) as unknown as DailyActivity[];
}

const cases = CASES.map(({ name, input }) => {
  const achievements = computeAchievements({
    entries: toLibraryEntries(input.entries),
    lifetimeWatchedMs: input.lifetimeWatchedMs,
    bestStreak: input.bestStreak,
  });
  const totalXp = totalXpEarned(achievements, input.lifetimeWatchedMs);
  const progress = computeLevelProgress(totalXp);
  return {
    name,
    input,
    expect: {
      // Icons and i18n keys are deliberately dropped: they are this app's presentation, not the
      // rules, and a Kotlin port has neither.
      achievements: achievements.map((a) => ({
        id: a.id,
        current: a.current,
        target: a.target,
        unlocked: a.unlocked,
        level: a.level,
        maxLevel: a.maxLevel,
        xpReward: a.xpReward,
        xpEarned: a.xpEarned,
      })),
      totalXp,
      level: progress.level,
      xpIntoLevel: progress.xpIntoLevel,
      xpForLevel: progress.xpForLevel,
    },
  };
});

const streakCases = STREAK_CASES.map(({ name, days }) => ({
  name,
  days,
  expect: computeStreaks(toSeries(days)),
}));

const document = {
  $comment:
    "Generated by scripts/generateProfileVectors.ts - do not edit by hand. The shared definition of " +
    "the profile rules, checked by profileRules.test.ts here and intended to be checked by the " +
    "Android port against the same file.",
  version: 1,
  constants: { xpPerWatchHour: 10, levelXpBase: 100, levelXpStep: 50, minutesPerCountedEpisode: 20 },
  cases,
  streakCases,
};

fs.writeFileSync(OUTPUT, JSON.stringify(document, null, 2) + "\n", "utf-8");
console.log(`wrote ${cases.length} rule cases and ${streakCases.length} streak cases to ${path.relative(process.cwd(), OUTPUT)}`);
