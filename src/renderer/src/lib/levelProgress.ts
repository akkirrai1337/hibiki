import type { Achievement } from "@/lib/achievements";

// 10 XP for every full five minutes watched (120 XP an hour) is the steady drip; achievements are the
// bigger, occasional bumps on top of it (see achievements.ts for how each tier's own xp is priced).
const WATCH_XP_INTERVAL_MS = 5 * 60_000;
const XP_PER_WATCH_INTERVAL = 10;

export function totalXpEarned(achievements: Achievement[], lifetimeWatchedMs: number): number {
  const fromWatching = Math.floor(lifetimeWatchedMs / WATCH_XP_INTERVAL_MS) * XP_PER_WATCH_INTERVAL;
  const fromAchievements = achievements.reduce((sum, a) => sum + a.xpEarned, 0);
  return fromWatching + fromAchievements;
}

// XP required to clear a level grows linearly (level 1: 100, level 2: +150, level 3: +200, ...) -
// leveling gets gradually harder without turning into a wall the way a steeper curve would.
const LEVEL_XP_BASE = 100;
const LEVEL_XP_STEP = 50;

function xpToClearLevel(level: number): number {
  return LEVEL_XP_BASE + (level - 1) * LEVEL_XP_STEP;
}

export interface LevelProgress {
  level: number; // starts at 1
  xpIntoLevel: number; // xp earned since reaching `level`
  xpForLevel: number; // xp needed to reach `level + 1`
  totalXp: number;
}

export function computeLevelProgress(totalXp: number): LevelProgress {
  let level = 1;
  let remaining = totalXp;
  while (remaining >= xpToClearLevel(level)) {
    remaining -= xpToClearLevel(level);
    level++;
  }
  return { level, xpIntoLevel: remaining, xpForLevel: xpToClearLevel(level), totalXp };
}
