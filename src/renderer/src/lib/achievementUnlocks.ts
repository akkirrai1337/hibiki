import { useEffect, useMemo, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { hibiki } from "@/lib/hibiki";
import { computeAchievements, tiersClearedInRange, withRecordedBest, type Achievement } from "@/lib/achievements";
import { useAchievementMarksStore } from "@/stores/achievementMarksStore";
import { buildActivitySeries, computeStreaks } from "@/components/StreakBadge";
import { useAchievementsStore } from "@/stores/achievementsStore";
import { useAchievementToastStore } from "@/stores/achievementToastStore";

// Just needs to be well past any realistic account age - listDailyActivity's own "days" param is
// a plain cutoff (now - days), not a page size, so this is a cheap way to ask for "everything"
// without a dedicated lifetime-totals endpoint. Kept in sync with profile.tsx's own copy of this
// constant (that page still runs its own 30-day ACTIVITY_DAYS query for its activity chart).
const LIFETIME_ACTIVITY_DAYS = 3650;

// Mounted once, at the app shell level (__root.tsx) rather than on the profile page - achievements
// can clear from watching an episode, adding to the library, or anything else that touches
// library/progress data, regardless of which page happens to be open at the time, so the "did
// anything just cross a new tier" check needs to keep running the whole session, not just while
// profile.tsx happens to be mounted. Also publishes the computed list to achievementsStore so the
// profile page can render its own achievement grid from the same computation instead of running
// this a second time (which would double up the tier-crossing detection below and record every new
// tier's XP twice).
export function useAchievementUnlocks(): void {
  const libraryQuery = useQuery({ queryKey: ["library"], queryFn: () => hibiki.library.list() });
  const lifetimeActivityQuery = useQuery({ queryKey: ["dailyActivity", LIFETIME_ACTIVITY_DAYS], queryFn: () => hibiki.progress.listDailyActivity(LIFETIME_ACTIVITY_DAYS) });
  const queryClient = useQueryClient();
  const setAchievements = useAchievementsStore((s) => s.setAchievements);
  const enqueueToast = useAchievementToastStore((s) => s.enqueue);

  const entries = libraryQuery.data ?? [];
  const lifetimeRows = lifetimeActivityQuery.data ?? [];
  const lifetimeWatchedMs = useMemo(() => lifetimeRows.reduce((sum, d) => sum + d.watchedMs, 0), [lifetimeRows]);
  // computeStreaks needs a gap-filled series (a missing day must break the run) - the raw rows
  // from the query only include days with actual activity, which would let a real gap of inactive
  // days silently count as "still consecutive".
  const lifetimeBestStreak = useMemo(
    () => computeStreaks(buildActivitySeries(lifetimeRows, LIFETIME_ACTIVITY_DAYS)).best,
    [lifetimeRows],
  );
  const markedTitles = useAchievementMarksStore((s) => s.titles);
  const markedCompleted = useAchievementMarksStore((s) => s.completed);
  const markedGenres = useAchievementMarksStore((s) => s.genres);
  const recordMarks = useAchievementMarksStore((s) => s.record);
  // The library topped up to its best ever figures, so removing titles never un-earns an achievement.
  const ruleEntries = useMemo(
    () => withRecordedBest(entries, { titles: markedTitles, completed: markedCompleted, genres: markedGenres }),
    [entries, markedTitles, markedCompleted, markedGenres],
  );
  const achievements = useMemo(
    () => computeAchievements({ entries: ruleEntries, lifetimeWatchedMs, bestStreak: lifetimeBestStreak }),
    [ruleEntries, lifetimeWatchedMs, lifetimeBestStreak],
  );

  // Recorded only once the library has really loaded, so an empty first render never counts as a figure.
  useEffect(() => {
    if (!libraryQuery.isSuccess) return;
    recordMarks({
      titles: ruleEntries.length,
      completed: ruleEntries.filter((e) => e.category === "completed").length,
      genres: new Set(ruleEntries.flatMap((e) => e.anime.genres ?? [])).size,
    });
  }, [libraryQuery.isSuccess, ruleEntries, recordMarks]);

  useEffect(() => { setAchievements(achievements); }, [achievements, setAchievements]);

  // Logs a history entry (and fires a toast) for every tier a family's level just passed through -
  // the only way to tell "just now" from "already had this" is comparing against the previous
  // render, so the very first successful load only seeds that baseline and reports nothing
  // (otherwise re-installing the app, or just the very first load after this feature shipped,
  // would dump the user's entire pre-existing progress into the history/toasts as if it all
  // happened in the same instant).
  const prevAchievementsRef = useRef<Achievement[] | null>(null);
  useEffect(() => {
    if (!libraryQuery.isSuccess || !lifetimeActivityQuery.isSuccess) return;
    const prev = prevAchievementsRef.current;
    prevAchievementsRef.current = achievements;
    if (!prev) return;

    const clearedTiers = achievements.flatMap((a) => {
      const fromLevel = prev.find((p) => p.id === a.id)?.level ?? 0;
      return a.level > fromLevel ? tiersClearedInRange(a.id, fromLevel, a.level) : [];
    });
    if (clearedTiers.length === 0) return;

    const now = Date.now();
    for (const tier of clearedTiers) {
      enqueueToast({ id: `${tier.id}-${now}`, icon: tier.icon, titleKey: `profile.achievements.${tier.id}.title`, xp: tier.xp });
    }
    Promise.all(clearedTiers.map((tier) => hibiki.xp.record(tier.id, tier.xp, now))).then(() => {
      queryClient.invalidateQueries({ queryKey: ["xpEvents"] });
    });
  }, [achievements, libraryQuery.isSuccess, lifetimeActivityQuery.isSuccess, queryClient, enqueueToast]);
}
