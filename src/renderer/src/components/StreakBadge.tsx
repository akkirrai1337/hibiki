import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { Flame } from "lucide-react";
import { cn } from "@/lib/cn";
import type { DailyActivity } from "@shared/types";

// Shared between the profile page (the badge next to your name) and the watch player (a toast
// announcing the same update mid-episode) - both need the exact same streak math and the exact
// same badge visuals/animation, just mounted in different places.

export const ACTIVITY_DAYS = 30;

function localDateKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Fills in the last `days` calendar days (local time) with zeros where nothing was recorded, so
 * the activity chart always has a full, evenly-spaced series instead of just the sparse rows. */
export function buildActivitySeries(rows: DailyActivity[], days: number): DailyActivity[] {
  const byDate = new Map(rows.map((r) => [r.date, r]));
  const now = new Date();
  const series: DailyActivity[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    const key = localDateKey(d);
    series.push(byDate.get(key) ?? { date: key, watchedMs: 0, completedCount: 0 });
  }
  return series;
}

export interface StreakInfo {
  current: number;
  best: number;
  atRisk: boolean;
}

/** Current streak = consecutive active days ending today, with one day of grace: a single fully
 * missed day doesn't zero it out, it just puts it at risk (see `atRisk`) - only a *second* miss
 * in a row actually breaks the run. Watching again pays the grace day back, so it's available
 * again for the next gap rather than being a one-time-ever thing. Today itself never counts
 * against this (or consumes the grace day) since it just hasn't finished yet. Best streak is the
 * longest such run anywhere in the window, under the same grace rule. Both are only as accurate
 * as the loaded window (30 days). */
export function computeStreaks(series: DailyActivity[]): StreakInfo {
  let best = 0;
  let run = 0;
  let graceUsed = false;
  for (let i = 0; i < series.length; i++) {
    const active = series[i].watchedMs > 0 || series[i].completedCount > 0;
    const isToday = i === series.length - 1;
    if (active) {
      run++;
      graceUsed = false;
      best = Math.max(best, run);
    } else if (isToday) {
      continue;
    } else if (!graceUsed) {
      graceUsed = true;
    } else {
      run = 0;
      graceUsed = false;
    }
  }
  const todayActive = series.length > 0 && (series[series.length - 1].watchedMs > 0 || series[series.length - 1].completedCount > 0);
  return { current: run, best, atRisk: run > 0 && !todayActive };
}

// A longer streak is worth more visual weight, not just a bigger number - each tier below is a
// full reskin of the badge (color, a standing ring for the top two, a slow color-cycle for the
// very top one), so the badge itself communicates "this one's serious" before you even read the
// count.
interface StreakTier {
  min: number;
  bg: string;
  text: string;
  glow: string;
  glow2: string;
  ring?: boolean;
  legendary?: boolean;
}
const STREAK_TIERS: StreakTier[] = [
  { min: 500, bg: "rgba(217,70,239,.18)", text: "#e879f9", glow: "#f472b6", glow2: "#a855f7", ring: true, legendary: true },
  { min: 250, bg: "rgba(234,179,8,.18)", text: "#facc15", glow: "#facc15", glow2: "#fb923c", ring: true },
  { min: 100, bg: "rgba(168,85,247,.16)", text: "#c084fc", glow: "#c084fc", glow2: "#f0abfc" },
  { min: 50, bg: "rgba(56,189,248,.16)", text: "#38bdf8", glow: "#38bdf8", glow2: "#7dd3fc" },
  { min: 25, bg: "rgba(239,68,68,.16)", text: "#f87171", glow: "#f87171", glow2: "#fb923c" },
  { min: 0, bg: "rgba(249,115,22,.15)", text: "#fb923c", glow: "#f97316", glow2: "#fbbf24" },
];
function streakTierFor(count: number): StreakTier {
  return STREAK_TIERS.find((tier) => count >= tier.min)!;
}

// Shown instead of the real tier whenever today hasn't been watched yet (see `atRisk` in
// computeStreaks) - a grace day means the count itself isn't lost yet, but the badge still needs
// to visibly nag "watch something today" rather than quietly staying its usual color as if
// nothing were at stake.
const STREAK_RISK_TIER: StreakTier = { min: 0, bg: "rgba(161,161,170,.15)", text: "#a1a1aa", glow: "#a1a1aa", glow2: "#a1a1aa" };

// Moved here from its own stat card, right in front of the name - a running streak is more of an
// identity badge ("I'm on day 7") than a plain stat, so it reads better living next to who you are
// than buried in a grid of numbers.
export function StreakBadge({
  current,
  best,
  atRisk,
  playOnMount = false,
}: {
  current: number;
  best: number;
  atRisk: boolean;
  // The profile badge lives on the page permanently and should only replay its pop/burst/rotate
  // animations on a genuine change, never on every page load/revisit - that's the default (false).
  // The watch-player toast is the opposite: it only ever mounts *because* the streak just changed,
  // so its first (and only) appearance IS the change, and needs to play immediately.
  playOnMount?: boolean;
}) {
  const { t } = useTranslation();
  // `current <= 0` bails before any of this renders, but the hooks above it still have to run on
  // every render regardless (streak hitting 0, or coming back from 0, can't change how many hooks
  // this component calls) - so the early return has to come after them, not before.
  const tier = atRisk ? STREAK_RISK_TIER : streakTierFor(current);
  const particles = useMemo(
    () => Array.from({ length: 8 }, (_, i) => ({ angle: (Math.PI * 2 * i) / 8, color: i % 2 === 0 ? tier.glow : tier.glow2 })),
    [tier.glow, tier.glow2],
  );
  if (current <= 0) return null;
  return (
    <span
      title={atRisk ? t("profile.streakAtRisk") : best > current ? t("profile.streakBest", { count: best }) : undefined}
      className={cn("relative inline-flex shrink-0 items-center gap-2 overflow-visible rounded-full py-1 pl-2 pr-2.5 text-sm font-bold transition-colors duration-500", tier.legendary && "legendary-glow")}
      style={{ background: tier.bg, color: tier.text, boxShadow: tier.min >= 250 ? `0 0 10px ${tier.glow}40` : undefined }}
    >
      {/* Stays lit for the whole time a top-tier streak holds, unlike everything else here which
          only plays once on the change that caused it - this one's a standing mark of the tier
          itself. */}
      {tier.ring && <span className="pointer-events-none absolute -inset-[3px] rounded-full" style={{ border: `1.5px solid ${tier.glow}`, opacity: 0.7 }} />}
      {/* `initial={false}` (the default, via `playOnMount`) on all four of these AnimatePresence
          groups means only a genuine change (a fresh day, crossing into a new tier) replays
          anything - just loading/revisiting the profile page never does. The watch-toast instance
          flips that to `initial={true}` since its mount *is* the change. */}
      <AnimatePresence initial={playOnMount}>
        <motion.span
          key={tier.min}
          className="pointer-events-none absolute -inset-1 rounded-full"
          style={{ border: `2px solid ${tier.glow}` }}
          initial={{ opacity: 0.9, scale: 1 }}
          animate={{ opacity: 0, scale: 2.4 }}
          transition={{ duration: 0.6, ease: "easeOut" }}
        />
      </AnimatePresence>
      <AnimatePresence initial={playOnMount}>
        <span key={current} className="pointer-events-none absolute inset-0">
          {particles.map((p, i) => (
            <motion.span
              key={i}
              className="absolute left-1/2 top-1/2 text-[10px]"
              style={{ color: p.color }}
              initial={{ x: "-50%", y: "-50%", opacity: 1 }}
              animate={{ x: `calc(-50% + ${Math.cos(p.angle) * 26}px)`, y: `calc(-50% + ${Math.sin(p.angle) * 26}px)`, opacity: 0 }}
              transition={{ duration: 0.55, ease: "easeOut" }}
            >
              ✦
            </motion.span>
          ))}
        </span>
      </AnimatePresence>
      <AnimatePresence initial={playOnMount}>
        <motion.span
          key={current}
          initial={{ rotate: -14, scale: 1.5 }}
          animate={{ rotate: 0, scale: 1 }}
          transition={{ type: "spring", stiffness: 450, damping: 15 }}
          className="flex"
        >
          <Flame className="h-4 w-4 shrink-0" style={{ fill: tier.text }} strokeWidth={0} />
        </motion.span>
      </AnimatePresence>
      {/* No `scale` here, unlike the flame - it's an icon, not text, and scaling text is exactly
          what was causing the "jitter": Chromium re-rasterizes a scaled glyph at a slightly
          different subpixel size every single frame, which reads as the digit itself trembling
          while it grows in (the same root cause behind the dropdown-menu shimmer fixed earlier,
          just via `scale` instead of a whole-panel transform). A plain opacity fade has no such
          effect since the glyph's own size and position never change, only its alpha blends in.
          Re-keyed on the value itself, not just re-rendered in place - a plain re-render would leave
          the number changing instantly with nothing to look at, so this remounts the span on every
          change and lets its own initial→animate replay a fresh "pop" each time. */}
      <AnimatePresence mode="popLayout" initial={playOnMount}>
        <motion.span
          key={current}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.25, ease: "easeOut" }}
          className="tabular-nums"
        >
          {current}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
