import type { LucideIcon } from "lucide-react";
import {
  Flag,
  Library,
  BookMarked,
  Archive,
  Trophy,
  Coffee,
  Popcorn,
  Sofa,
  AlarmClock,
  Gauge,
  Sprout,
  Rocket,
  Gem,
  Star,
  MonitorPlay,
  Video,
  Projector,
  Theater,
  Medal,
  Crown,
  Flame,
  Zap,
  CalendarRange,
  Clock,
  Timer,
  Hourglass,
  Tv,
  Film,
  Clapperboard,
  Palette,
  Sparkles,
  BookOpen,
} from "lucide-react";
import type { LibraryEntry } from "@shared/types";

export interface Achievement {
  id: string;
  icon: LucideIcon;
  titleKey: string;
  descriptionKey: string;
  current: number;
  target: number;
  unlocked: boolean;
  // Tiers already cleared / total tiers in this achievement's family - e.g. having unlocked
  // "Коллекционер" and "Энтузиаст" but not yet "Куратор" is level 2 of 3. maxLevel is 1 for a
  // plain, untiered achievement (first_title), which doesn't get a level indicator in the UI.
  level: number;
  maxLevel: number;
  // XP for clearing the tier this card is currently showing - the one still in progress, or (once
  // maxed) the last one it already cleared.
  xpReward: number;
  // Total XP already banked from every tier of this family cleared so far (0 if none yet) - what
  // levelProgress.ts actually sums into the bar, kept alongside xpReward rather than recomputed
  // from `level` externally, since tiers no longer pay a uniform amount by position.
  xpEarned: number;
}

interface AchievementInput {
  entries: LibraryEntry[];
  lifetimeWatchedMs: number;
  bestStreak: number;
}

// A watched "episode" here isn't the real episode-completion event (that's marathoner_10/finisher,
// via library categories) - it's just watched time bucketed into ~episode-length chunks, per the
// user's own request, so someone who watches movies/OVAs (which never trip the per-episode
// "completed" flag the same way a TV episode does) still accumulates toward this one.
const MINUTES_PER_COUNTED_EPISODE = 20;

export interface AchievementTier {
  // Reuses the same profile.achievements.<id> locale keys the old flat, one-tier-per-card design
  // used - collector_10/25/50 (Коллекционер/Энтузиаст/Куратор) become tiers 1/2/3 of a single
  // "collector" family card instead of three separate always-visible cards, same text, no new
  // translations needed for tiers that already existed.
  id: string;
  icon: LucideIcon;
  target: number;
  // Set per-tier by hand, not derived from tier position - a flat "tier 1/2/3 always pays
  // 50/100/200" schedule badly misjudged real difficulty (24 hours of actual watching and adding
  // 10 titles to a list were both "tier 1", paid the same 50 XP). This should track how much real
  // effort/time a tier actually costs, family by family, not where it sits in its own list.
  xp: number;
}

// first_title is a single, untiered achievement everywhere else in this file, but the XP-history
// feature (see tiersClearedInRange) needs every family - tiered or not - addressable the same
// way, so it gets a one-entry "family" of its own here too.
const FIRST_TITLE_TIER: AchievementTier[] = [{ id: "first_title", icon: Flag, target: 1, xp: 5 }];
const COLLECTOR_TIERS: AchievementTier[] = [
  { id: "collector_10", icon: Library, target: 10, xp: 10 },
  { id: "collector_25", icon: BookMarked, target: 25, xp: 20 },
  { id: "collector_50", icon: Archive, target: 50, xp: 40 },
];
const FINISHER_TIERS: AchievementTier[] = [
  { id: "finisher", icon: Trophy, target: 1, xp: 200 },
  { id: "marathoner_10", icon: Medal, target: 10, xp: 800 },
  { id: "marathoner_50", icon: Crown, target: 50, xp: 3000 },
];
// More, closer steps than a handful of big ones: a new tier should be something you meet every few
// sessions, not once a season. Each family's rewards climb with the effort behind the step.
const STREAK_TIERS: AchievementTier[] = [
  { id: "streak_3", icon: Sprout, target: 3, xp: 60 },
  { id: "streak_7", icon: Flame, target: 7, xp: 160 },
  { id: "streak_14", icon: Zap, target: 14, xp: 360 },
  { id: "streak_21", icon: Rocket, target: 21, xp: 600 },
  { id: "streak_30", icon: CalendarRange, target: 30, xp: 1000 },
  { id: "streak_60", icon: Gem, target: 60, xp: 1800 },
  { id: "streak_100", icon: Star, target: 100, xp: 3200 },
];
const WATCH_TIERS: AchievementTier[] = [
  { id: "watch_3h", icon: Coffee, target: 3, xp: 120 },
  { id: "watch_6h", icon: Popcorn, target: 6, xp: 240 },
  { id: "watch_12h", icon: Sofa, target: 12, xp: 440 },
  { id: "watch_24h", icon: Clock, target: 24, xp: 800 },
  { id: "watch_50h", icon: AlarmClock, target: 50, xp: 1400 },
  { id: "watch_100h", icon: Timer, target: 100, xp: 2400 },
  { id: "watch_250h", icon: Gauge, target: 250, xp: 4000 },
  { id: "watch_500h", icon: Hourglass, target: 500, xp: 7000 },
];
const EPISODES_TIERS: AchievementTier[] = [
  { id: "episodes_10", icon: MonitorPlay, target: 10, xp: 80 },
  { id: "episodes_25", icon: Video, target: 25, xp: 200 },
  { id: "episodes_50", icon: Tv, target: 50, xp: 500 },
  { id: "episodes_100", icon: Film, target: 100, xp: 1000 },
  { id: "episodes_200", icon: Projector, target: 200, xp: 1600 },
  { id: "episodes_300", icon: Clapperboard, target: 300, xp: 2600 },
  { id: "episodes_500", icon: Theater, target: 500, xp: 4400 },
];
const GENRES_TIERS: AchievementTier[] = [
  { id: "genre_explorer", icon: Palette, target: 10, xp: 25 },
  { id: "genre_explorer_10", icon: Sparkles, target: 20, xp: 60 },
  { id: "genre_explorer_15", icon: BookOpen, target: 30, xp: 150 },
];

export const FAMILY_TIERS: Record<string, AchievementTier[]> = {
  first_title: FIRST_TITLE_TIER,
  collector: COLLECTOR_TIERS,
  finisher: FINISHER_TIERS,
  streak: STREAK_TIERS,
  watch: WATCH_TIERS,
  episodes: EPISODES_TIERS,
  genres: GENRES_TIERS,
};

// Which tier(s) a family's level just passed through going from `fromLevel` to `toLevel` - used
// to log XP-history entries (see xpEvents in profile.tsx) for exactly what was cleared, without
// needing its own separate copy of these thresholds.
export function tiersClearedInRange(familyId: string, fromLevel: number, toLevel: number): AchievementTier[] {
  return (FAMILY_TIERS[familyId] ?? []).slice(fromLevel, toLevel);
}

// Every tier across every family, keyed by its own id - resolves an XP-history row's `kind` back
// to an icon/title without the caller needing to know which family it belongs to.
export const ACHIEVEMENT_TIER_BY_ID: Record<string, AchievementTier> = Object.fromEntries(
  Object.values(FAMILY_TIERS)
    .flat()
    .map((tier) => [tier.id, tier]),
);

// Shared with levelProgress.ts, which needs the same hours figure the achievement cards show -
// rounded to 1 decimal, since the raw division is a long, jittery float ("0.6467858086111112")
// that's meaningless precision either place it's used.
export function watchedHoursFrom(lifetimeWatchedMs: number): number {
  return Math.round((lifetimeWatchedMs / 3_600_000) * 10) / 10;
}

function single(id: string, icon: LucideIcon, current: number, target: number, xp: number): Achievement {
  const unlocked = current >= target;
  return {
    id,
    icon,
    titleKey: `profile.achievements.${id}.title`,
    descriptionKey: `profile.achievements.${id}.description`,
    current: Math.min(current, target),
    target,
    unlocked,
    level: unlocked ? 1 : 0,
    maxLevel: 1,
    xpReward: xp,
    xpEarned: unlocked ? xp : 0,
  };
}

// One card per family: shows whichever tier is currently in progress (or the last one, fully
// unlocked, once every tier is cleared) - not every tier as its own separate card. Clearing tier 1
// immediately swaps the card over to tier 2 as the new goal rather than leaving tier 1 sitting
// there permanently marked done, so "уровни" reads as one badge leveling up, not a growing pile of
// finished achievements next to the one you're still working on.
function leveled(familyId: string, tiers: AchievementTier[], current: number): Achievement {
  let level = 0;
  for (const tier of tiers) {
    if (current >= tier.target) level++;
    else break;
  }
  const maxed = level >= tiers.length;
  const activeTier = tiers[maxed ? tiers.length - 1 : level];
  const xpEarned = tiers.slice(0, level).reduce((sum, tier) => sum + tier.xp, 0);
  return {
    id: familyId,
    icon: activeTier.icon,
    titleKey: `profile.achievements.${activeTier.id}.title`,
    descriptionKey: `profile.achievements.${activeTier.id}.description`,
    current: Math.min(current, activeTier.target),
    target: activeTier.target,
    unlocked: maxed,
    level,
    maxLevel: tiers.length,
    xpReward: activeTier.xp,
    xpEarned,
  };
}

export interface AchievementMarks {
  titles: number;
  completed: number;
  genres: number;
}

// Tops the library up to the best figures ever recorded, so a library that has since shrunk does not
// un-earn library-based achievements. Neutral stand-in entries are used, so computeAchievements and its
// shared vectors stay exactly as they are.
export function withRecordedBest(entries: LibraryEntry[], marks: AchievementMarks): LibraryEntry[] {
  const completedPad = Math.max(0, marks.completed - entries.filter((e) => e.category === "completed").length);
  const genrePad = Math.max(0, marks.genres - new Set(entries.flatMap((e) => e.anime.genres ?? [])).size);
  const padding: LibraryEntry[] = [];
  for (let i = 0; i < completedPad; i++) {
    padding.push({ category: "completed", anime: { genres: [] } } as unknown as LibraryEntry);
  }
  if (genrePad > 0) {
    const genres = Array.from({ length: genrePad }, (_, i) => `\u0000best-${i}`);
    padding.push({ category: "other", anime: { genres } } as unknown as LibraryEntry);
  }
  for (let i = entries.length + padding.length; i < marks.titles; i++) {
    padding.push({ category: "other", anime: { genres: [] } } as unknown as LibraryEntry);
  }
  return padding.length ? [...entries, ...padding] : entries;
}

// Computed entirely from data already loaded elsewhere on this page (library entries, a lifetime
// activity query, the same streak math the stat cards use) - no separate achievements table, just
// thresholds read off state that already exists for other reasons.
export function computeAchievements({ entries, lifetimeWatchedMs, bestStreak }: AchievementInput): Achievement[] {
  const completedCount = entries.filter((e) => e.category === "completed").length;
  const genreCount = new Set(entries.flatMap((e) => e.anime.genres ?? [])).size;
  const watchedHours = watchedHoursFrom(lifetimeWatchedMs);
  const watchedEpisodeEquivalent = Math.floor(lifetimeWatchedMs / (MINUTES_PER_COUNTED_EPISODE * 60_000));

  return [
    single("first_title", Flag, entries.length, FIRST_TITLE_TIER[0].target, FIRST_TITLE_TIER[0].xp),
    // Just adding titles to a list - no watching required, trivially fast even at 50.
    leveled("collector", COLLECTOR_TIERS, entries.length),
    // Real watching, ~4-5h per average title - 10 completions is ~2 work-weeks of anime, 50 is a
    // serious hobby's worth of hours.
    leveled("finisher", FINISHER_TIERS, completedCount),
    // Daily engagement, not raw hours - a 30-day streak takes a full month of actually showing up
    // rather than any one long session, which the flat 50/100/200 schedule badly undersold.
    leveled("streak", STREAK_TIERS, bestStreak),
    // Lifetime hours actually watched - no shortcuts, the most direct real-time-cost metric there
    // is. 24h alone is a full day of continuous watching; 500h is a genuinely huge commitment.
    leveled("watch", WATCH_TIERS, watchedHours),
    // Same underlying watched-time metric as "watch" above, just bucketed into ~episode-length
    // chunks (50 ≈ 16-17h) - priced on the same real-hours basis as that family, not as a separate
    // cheap grind.
    leveled("episodes", EPISODES_TIERS, watchedEpisodeEquivalent),
    // Genre variety is a side effect of what you happen to add, not real effort on its own - kept
    // modest, not scaled up with the watch-time families. The tier ids keep their old numbers (5/10/15)
    // so history and the Android app still match; the targets are what the tiers ask for.
    leveled("genres", GENRES_TIERS, genreCount),
  ];
}
