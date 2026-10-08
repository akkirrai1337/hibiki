import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { AnimatePresence, motion } from "motion/react";
import { Pencil, User, Check, ChevronRight, Film, ImageUp, Library, Clock, CheckCircle2, Gauge, Settings, Sparkles, Trash2, X } from "lucide-react";
import { hibiki, profileBannerUrl } from "@/lib/hibiki";
import { Modal } from "@/components/Modal";
import { animeTitle } from "@/components/AnimeCard";
import { ContinueWatchingRow } from "@/components/ContinueWatchingRow";
import { useContinueWatching } from "@/lib/continueWatching";
import { useProfileStore } from "@/stores/profileStore";
import { useUiStore } from "@/stores/uiStore";
import { useAchievementsStore } from "@/stores/achievementsStore";
import { cn } from "@/lib/cn";
import { isMobile } from "@/lib/mobile";
import { useSourceUpdateCount } from "@/lib/sourceUpdates";
import { BottomSheet, SheetOption } from "@/components/BottomSheet";
import { LIBRARY_CATEGORY_ICONS, LIBRARY_CATEGORY_LABEL_KEYS } from "@/lib/libraryCategories";
import { AchievementGrid } from "@/components/AchievementCards";
import { ACHIEVEMENT_TIER_BY_ID } from "@/lib/achievements";
import { computeLevelProgress, totalXpEarned, type LevelProgress } from "@/lib/levelProgress";
import { ACTIVITY_DAYS, buildActivitySeries, computeStreaks, StreakBadge, type StreakInfo } from "@/components/StreakBadge";
import type { DailyActivity, LibraryEntry, XpEvent } from "@shared/types";

const RECENT_LIMIT = 5;
const CONTINUE_LIMIT = 4;
// Just needs to be well past any realistic account age - listDailyActivity's own "days" param is
// a plain cutoff (now - days), not a page size, so this is a cheap way to ask for "everything"
// without a dedicated lifetime-totals endpoint. Matches useAchievementUnlocks's own copy of this
// constant - same query key, so react-query serves both from one shared cache entry rather than
// fetching it twice.
const LIFETIME_ACTIVITY_DAYS = 3650;
const DEFAULT_NAME = "Hibiki";

function shortDayLabel(dateKey: string, locale: string): string {
  const [y, m, d] = dateKey.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(locale, { day: "numeric", month: "short" });
}

function relativeDate(epochMs: number, t: TFunction, locale: string): string {
  const days = Math.floor((Date.now() - epochMs) / 86_400_000);
  if (days <= 0) return t("profile.today");
  if (days === 1) return t("profile.yesterday");
  if (days < 7) return t("profile.daysAgo", { count: days });
  return new Date(epochMs).toLocaleDateString(locale, { day: "numeric", month: "short" });
}

export function ProfilePage() {
  const { t, i18n } = useTranslation();
  const name = useProfileStore((s) => s.name);
  const setName = useProfileStore((s) => s.setName);
  const avatarDataUrl = useProfileStore((s) => s.avatarDataUrl);
  const setAvatarDataUrl = useProfileStore((s) => s.setAvatarDataUrl);
  const bannerFilename = useProfileStore((s) => s.bannerFilename);
  const setBannerFilename = useProfileStore((s) => s.setBannerFilename);

  const libraryQuery = useQuery({ queryKey: ["library"], queryFn: () => hibiki.library.list() });
  // Same ["sources"] key index.tsx/library.tsx already query - without this, ContinueWatchingRow
  // below had no installed-source map to check a card's source against, so every card here always
  // fell back to the "source removed" badge (see knownSourcesStore), installed or not.
  const sourcesQuery = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const sourceById = useMemo(() => new Map((sourcesQuery.data ?? []).map((s) => [s.id, s])), [sourcesQuery.data]);
  const activityQuery = useQuery({ queryKey: ["dailyActivity", ACTIVITY_DAYS], queryFn: () => hibiki.progress.listDailyActivity(ACTIVITY_DAYS) });
  // Separate from the 30-day one above - the level bar's "XP from watching hours" component (see
  // levelProgress.ts) needs a real lifetime total, not just what the visible activity chart
  // covers. Same query key useAchievementUnlocks() itself fetches, in __root.tsx - one shared
  // cache entry, not a second live request just because this page also wants a piece of it.
  const lifetimeActivityQuery = useQuery({ queryKey: ["dailyActivity", LIFETIME_ACTIVITY_DAYS], queryFn: () => hibiki.progress.listDailyActivity(LIFETIME_ACTIVITY_DAYS) });

  const entries = libraryQuery.data ?? [];
  const completedTitlesCount = useMemo(() => entries.filter((e) => e.category === "completed").length, [entries]);

  const activitySeries = useMemo(() => buildActivitySeries(activityQuery.data ?? [], ACTIVITY_DAYS), [activityQuery.data]);
  const totalWatchedMs = useMemo(() => activitySeries.reduce((sum, d) => sum + d.watchedMs, 0), [activitySeries]);
  const totalCompleted = useMemo(() => activitySeries.reduce((sum, d) => sum + d.completedCount, 0), [activitySeries]);
  // Episodes/week over the same 30-day window the other cards already use - distinct from the raw
  // "episodes watched" total next to it (a rate vs. a count), and from the streak badge (which
  // only tracks whether a day had *any* activity, not how much).
  const weeklyPace = useMemo(() => Math.round((totalCompleted / (ACTIVITY_DAYS / 7)) * 10) / 10, [totalCompleted]);
  const { current: currentStreak, best: bestStreak, atRisk: streakAtRisk } = useMemo(() => computeStreaks(activitySeries), [activitySeries]);

  // Plays the badge's pop/burst animation once for an increment that happened elsewhere (the watch
  // player) while this page wasn't open to show its own version of it - see profileCelebratedStreak
  // for why this needs to be its own persisted value rather than reusing the player toast's. Read
  // directly during render (not via effect/state) because the value that matters is whatever it is
  // at the exact moment the badge's AnimatePresence groups first mount - by design that's the same
  // moment `currentStreak` first becomes > 0 (StreakBadge itself renders nothing before then), so
  // the query has necessarily already resolved by then and this can't race a stale default.
  const profileCelebratedStreak = useUiStore((s) => s.profileCelebratedStreak);
  const setProfileCelebratedStreak = useUiStore((s) => s.setProfileCelebratedStreak);
  const playStreakOnMount = profileCelebratedStreak !== -1 && currentStreak > profileCelebratedStreak;
  useEffect(() => {
    if (activityQuery.isSuccess && currentStreak !== profileCelebratedStreak) setProfileCelebratedStreak(currentStreak);
  }, [activityQuery.isSuccess, currentStreak, profileCelebratedStreak, setProfileCelebratedStreak]);

  const lifetimeRows = lifetimeActivityQuery.data ?? [];
  const lifetimeWatchedMs = useMemo(() => lifetimeRows.reduce((sum, d) => sum + d.watchedMs, 0), [lifetimeRows]);
  // Computed and kept fresh by useAchievementUnlocks (mounted once in __root.tsx, not here) - that
  // hook also owns detecting a newly-crossed tier and logging/toasting it, which has to keep
  // running regardless of whether this page happens to be open (most tiers actually clear from
  // the watch page, via "finisher"). Reading its output back out here just avoids recomputing the
  // same achievement list a second time.
  const achievements = useAchievementsStore((s) => s.achievements);

  const xpEventsQuery = useQuery({ queryKey: ["xpEvents"], queryFn: () => hibiki.xp.list() });
  const queryClient = useQueryClient();
  const [clearHistoryOpen, setClearHistoryOpen] = useState(false);
  const clearXpHistory = async () => {
    await hibiki.xp.clear();
    setClearHistoryOpen(false);
    void queryClient.invalidateQueries({ queryKey: ["xpEvents"] });
  };

  // Mostly watch time, with unlocking (not just accumulating) achievement tiers as the bigger,
  // occasional bumps - see levelProgress.ts for the exact weighting.
  const levelProgress = useMemo(
    () => computeLevelProgress(totalXpEarned(achievements, lifetimeWatchedMs)),
    [achievements, lifetimeWatchedMs],
  );

  const recent = useMemo(() => [...entries].sort((a, b) => b.addedAt - a.addedAt).slice(0, RECENT_LIMIT), [entries]);
  // Shared with the home page's own "continue watching" row - see useContinueWatching, which
  // caches per-title lookups under query keys both pages agree on, so whichever page the user
  // visits first does the actual fetching and the other reads it straight back out of the cache.
  const { hasHistory } = useContinueWatching();

  // Every number on this page (streak, level, stat cards, activity chart, XP history) depends on
  // one of these - rendering the real layout before they've resolved just showed a page full of
  // zeroes and empty sections that then visibly jumped to their real values a moment later, which
  // read as broken/flaky rather than "still loading". `sourcesQuery` is deliberately left out -
  // it's only used for a card's source badge (a cosmetic, non-blocking detail), and library/
  // lifetime-activity are already warmed at the app shell level (see __root.tsx's own
  // useAchievementUnlocks), so in practice this only actually shows on a genuinely cold first
  // load, not once per profile visit.
  const isLoading = libraryQuery.isLoading || activityQuery.isLoading || lifetimeActivityQuery.isLoading || xpEventsQuery.isLoading;
  if (isLoading) return <ProfileSkeleton />;

  if (isMobile) {
    return (
      <>
        <MobileProfileLayout
          header={
            <div className="flex items-end gap-3.5">
              <AvatarPicker avatarDataUrl={avatarDataUrl} onChange={setAvatarDataUrl} />
              <div className="min-w-0 flex-1 pb-1.5"><NameEditor name={name ?? DEFAULT_NAME} onChange={setName} streak={{ current: currentStreak, best: bestStreak, atRisk: streakAtRisk }} playStreakOnMount={playStreakOnMount} /></div>
            </div>
          }
          bannerFilename={bannerFilename}
          onBannerChange={setBannerFilename}
          levelProgress={levelProgress}
          watchedMs={totalWatchedMs}
          episodes={totalCompleted}
          titles={completedTitlesCount}
          weeklyPace={weeklyPace}
          librarySize={entries.length}
          activitySeries={activitySeries}
          achievements={achievements}
          xpEvents={xpEventsQuery.data ?? []}
          onClearXpHistory={() => setClearHistoryOpen(true)}
          recent={recent}
        />
        <AnimatePresence>
          {clearHistoryOpen && <ClearXpHistoryDialog onConfirm={clearXpHistory} onDismiss={() => setClearHistoryOpen(false)} />}
        </AnimatePresence>
      </>
    );
  }

  return (
    <div className="min-h-full bg-app-bg pb-16">
      <ProfileHeader
        name={name ?? DEFAULT_NAME}
        onNameChange={setName}
        avatarDataUrl={avatarDataUrl}
        onAvatarChange={setAvatarDataUrl}
        bannerFilename={bannerFilename}
        onBannerChange={setBannerFilename}
        levelProgress={levelProgress}
        streak={{ current: currentStreak, best: bestStreak, atRisk: streakAtRisk }}
        playStreakOnMount={playStreakOnMount}
      />

      {/* A 4th column only kicks in once the window is wide enough to actually give it room
          (2xl, 1536px+) - below that, XP history just stacks under achievements inside their
          shared column instead of squeezing into its own sliver. */}
      <div className="grid max-w-6xl grid-cols-1 gap-8 px-8 pt-8 lg:grid-cols-3 2xl:max-w-[1600px] 2xl:grid-cols-4">
        <div className="space-y-8 lg:col-span-2">
          {/* The streak card moved up next to the name (see StreakBadge in ProfileHeader) - this
              slot now shows library size instead of just dropping to 3 cards. */}
          <div className="grid grid-cols-5 gap-4">
            <StatCard icon={Film} label={t("profile.statCompletedTitles")} value={completedTitlesCount} />
            <StatCard icon={Library} label={t("profile.statLibrarySize")} value={entries.length} />
            <StatCard icon={Clock} label={t("profile.statWatchTime")} value={t("profile.hours", { hours: (totalWatchedMs / 3_600_000).toFixed(1) })} />
            <StatCard icon={CheckCircle2} label={t("profile.episodesCompleted")} value={totalCompleted} />
            <StatCard icon={Gauge} label={t("profile.statPace")} value={t("common.episodesShort", { count: weeklyPace })} />
          </div>

          {hasHistory && (
            <section>
              <h2 className="mb-3 text-sm font-bold text-text">{t("profile.continueWatchingTitle")}</h2>
              <ContinueWatchingRow limit={CONTINUE_LIMIT} sourceById={sourceById} />
            </section>
          )}

          <section>
            <h2 className="mb-4 text-sm font-bold text-text">{t("profile.activityTitle")}</h2>
            <ActivityBars series={activitySeries} locale={i18n.language} />
          </section>

          <section>
            <h2 className="mb-3 text-sm font-bold text-text">{t("profile.recentTitle")}</h2>
            {recent.length === 0 ? (
              <p className="text-sm text-muted">{t("profile.recentEmpty")}</p>
            ) : (
              <div className="flex flex-col gap-1">
                {recent.map((entry) => <RecentRow key={`${entry.sourceId}:${entry.animeId}`} entry={entry} locale={i18n.language} />)}
              </div>
            )}
          </section>
        </div>

        <div className="grid grid-cols-1 gap-8 lg:col-span-1 2xl:col-span-2 2xl:grid-cols-2">
          <section>
            <h2 className="mb-3 text-sm font-bold text-text">{t("profile.achievementsTitle")}</h2>
            <AchievementGrid achievements={achievements} />
          </section>

          <section>
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="text-sm font-bold text-text">{t("profile.xpHistoryTitle")}</h2>
              {(xpEventsQuery.data ?? []).length > 0 && (
                <button
                  onClick={() => setClearHistoryOpen(true)}
                  aria-label={t("profile.xpHistoryClear")}
                  title={t("profile.xpHistoryClear")}
                  className="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.06] hover:text-text"
                >
                  <Trash2 className="h-4 w-4" strokeWidth={2} />
                </button>
              )}
            </div>
            {(xpEventsQuery.data ?? []).length === 0 ? (
              <p className="text-sm text-muted">{t("profile.xpHistoryEmpty")}</p>
            ) : (
              <div className="flex flex-col gap-1">
                {(xpEventsQuery.data ?? []).map((event) => <XpHistoryRow key={event.id} event={event} locale={i18n.language} />)}
              </div>
            )}
          </section>
        </div>
      </div>
      <AnimatePresence>
        {clearHistoryOpen && <ClearXpHistoryDialog onConfirm={clearXpHistory} onDismiss={() => setClearHistoryOpen(false)} />}
      </AnimatePresence>
    </div>
  );
}

/**
 * The phone's profile - its own layout, not the desktop page squeezed: a banner header with the
 * avatar over its edge and the level under the name; the three numbers that matter in one card;
 * the month's activity; achievements as a row of badges; and the rest (XP history, recently added,
 * settings, sources) as rows that open what they name.
 */
function MobileProfileLayout({
  header,
  bannerFilename,
  onBannerChange,
  levelProgress,
  watchedMs,
  episodes,
  titles,
  weeklyPace,
  librarySize,
  activitySeries,
  achievements,
  xpEvents,
  onClearXpHistory,
  recent,
}: {
  header: React.ReactNode;
  bannerFilename: string | null;
  onBannerChange: (filename: string | null) => void;
  levelProgress: LevelProgress;
  watchedMs: number;
  episodes: number;
  titles: number;
  weeklyPace: number;
  librarySize: number;
  activitySeries: DailyActivity[];
  achievements: ReturnType<typeof useAchievementsStore.getState>["achievements"];
  xpEvents: XpEvent[];
  onClearXpHistory: () => void;
  recent: LibraryEntry[];
}) {
  const { t, i18n } = useTranslation();
  const sourceUpdateCount = useSourceUpdateCount();
  const [sheet, setSheet] = useState<"achievements" | "xp" | "recent" | null>(null);
  const unlocked = achievements.filter((a) => a.unlocked).length;
  // Earned first, then the nearest to done - the same order as the full list.
  const badges = useMemo(() => [...achievements].sort((a, b) => Number(b.unlocked) - Number(a.unlocked) || b.current / b.target - a.current / a.target), [achievements]);

  return (
    <div className="min-h-full bg-app-bg pb-4">
      <div className="relative -mt-[var(--safe-top)]">
        {/* Dissolves into whatever is behind the page - a mask, not a fade to the plain background
            colour, which against a background theme left a hard edge right under the name row. */}
        <div
          className="relative overflow-hidden bg-gradient-to-br from-accent/25 via-text/[.04] to-transparent"
          style={{
            // Without a banner there is nothing to show up there: only the row of corner buttons.
            height: bannerFilename ? "calc(10rem + var(--safe-top))" : "calc(3.75rem + var(--safe-top))",
            maskImage: "linear-gradient(to bottom, #000 45%, transparent)",
            WebkitMaskImage: "linear-gradient(to bottom, #000 45%, transparent)",
          }}
        >
          <BannerMedia filename={bannerFilename} />
          {/* What a new user looks for first - settings, and where the anime comes from - up in the
              corner where an app keeps them, with the banner's own edit beside. */}
          <div className="absolute right-3 flex items-center gap-2" style={{ top: "calc(0.5rem + var(--safe-top))" }}>
            <BannerActions filename={bannerFilename} onChange={onBannerChange} inline />
            {/* Sources live in Settings on the phone; their update badge rides on its button. */}
            <Link to="/settings" aria-label={t("nav.settings")} className={cn(MOBILE_CORNER_BUTTON, "relative")}>
              <Settings className="h-[18px] w-[18px]" strokeWidth={2} />
              {sourceUpdateCount > 0 && <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">{sourceUpdateCount > 9 ? "9+" : sourceUpdateCount}</span>}
            </Link>
          </div>
        </div>
        <div className={cn("relative px-4", bannerFilename ? "-mt-14" : "mt-1")}>{header}</div>
      </div>

      <div className="space-y-3 px-4 pt-4">
        <div className="mb-1"><LevelBar levelProgress={levelProgress} /></div>

        <div className="grid grid-cols-3 divide-x divide-border rounded-2xl border border-border bg-text/[.03] py-3.5 text-center">
          <MobileStat value={t("profile.hours", { hours: (watchedMs / 3_600_000).toFixed(1) })} label={t("profile.statWatchTime")} />
          <MobileStat value={episodes} label={t("profile.episodesCompleted")} />
          <MobileStat value={titles} label={t("profile.statCompletedTitles")} />
        </div>
        <p className="px-1 text-xs text-muted">
          {t("profile.statPace")}: {t("common.episodesShort", { count: weeklyPace })} · {t("profile.statLibrarySize")}: {librarySize}
        </p>

        <section className="rounded-2xl border border-border bg-text/[.03] p-3.5">
          <MobileActivityBars series={activitySeries} locale={i18n.language} />
        </section>

        <section className="pt-2">
          <button onClick={() => setSheet("achievements")} className="mb-2.5 flex w-full items-center justify-between">
            <h2 className="text-[15px] font-bold text-text">{t("profile.achievementsTitle")}</h2>
            <span className="flex items-center gap-0.5 text-xs font-semibold text-muted">{unlocked} / {achievements.length}<ChevronRight className="h-4 w-4" strokeWidth={2.25} /></span>
          </button>
          <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4">
            {badges.map((a) => (
              <button key={a.id} onClick={() => setSheet("achievements")} className="flex w-[4.25rem] shrink-0 flex-col items-center gap-1.5">
                {/* A ring filled as far as the next tier is done - the same progress the full list shows. */}
                <span className="rounded-full p-[2px]" style={{ background: `conic-gradient(rgb(var(--color-accent)) ${Math.min(1, a.current / Math.max(1, a.target)) * 360}deg, rgb(var(--color-text) / 0.08) 0deg)` }}>
                  <span className={cn("flex h-[52px] w-[52px] items-center justify-center rounded-full bg-app-bg", a.unlocked ? "text-accent-text" : "text-muted/60")}>
                    <a.icon className="h-6 w-6" strokeWidth={1.9} />
                  </span>
                </span>
                <span className={cn("line-clamp-2 text-center text-[11px] leading-tight", a.unlocked ? "text-text/85" : "text-muted/60")}>{t(a.titleKey)}</span>
              </button>
            ))}
          </div>
        </section>

        <div className="overflow-hidden rounded-2xl border border-border bg-text/[.03]">
          <MobileMenuRow icon={Sparkles} label={t("profile.xpHistoryTitle")} detail={xpEvents.length || undefined} onClick={() => setSheet("xp")} />
          <MobileMenuRow icon={Clock} label={t("profile.recentTitle")} detail={recent.length || undefined} onClick={() => setSheet("recent")} />
        </div>
      </div>

      <BottomSheet open={sheet === "achievements"} onClose={() => setSheet(null)} title={t("profile.achievementsTitle")}>
        <div className="px-2"><AchievementGrid achievements={achievements} /></div>
      </BottomSheet>
      <BottomSheet
        open={sheet === "xp"}
        onClose={() => setSheet(null)}
        title={t("profile.xpHistoryTitle")}
        footer={xpEvents.length > 0 ? <button onClick={onClearXpHistory} className="w-full rounded-full py-2.5 text-sm font-semibold text-rose-400 active:bg-text/[.06]">{t("profile.xpHistoryClear")}</button> : undefined}
      >
        {xpEvents.length === 0 ? <p className="px-4 py-6 text-center text-sm text-muted">{t("profile.xpHistoryEmpty")}</p> : xpEvents.map((event) => <XpHistoryRow key={event.id} event={event} locale={i18n.language} />)}
      </BottomSheet>
      <BottomSheet open={sheet === "recent"} onClose={() => setSheet(null)} title={t("profile.recentTitle")}>
        {recent.length === 0 ? <p className="px-4 py-6 text-center text-sm text-muted">{t("profile.recentEmpty")}</p> : recent.map((entry) => <RecentRow key={`${entry.sourceId}:${entry.animeId}`} entry={entry} locale={i18n.language} />)}
      </BottomSheet>
    </div>
  );
}

const MOBILE_CORNER_BUTTON = "flex h-10 w-10 items-center justify-center rounded-full bg-black/45 text-white ring-1 ring-white/10 backdrop-blur-md active:bg-black/70";

function MobileStat({ value, label }: { value: string | number; label: string }) {
  return (
    <div className="min-w-0 px-2">
      <p className="truncate text-[19px] font-bold tabular-nums text-text">{value}</p>
      <p className="mt-0.5 line-clamp-2 text-[11px] leading-tight text-muted">{label}</p>
    </div>
  );
}

function MobileMenuRow({ icon: Icon, label, detail, badge, to, onClick }: { icon: typeof Film; label: string; detail?: number; badge?: number; to?: "/settings" | "/sources"; onClick?: () => void }) {
  const body = (
    <>
      <Icon className="h-[19px] w-[19px] shrink-0 text-accent-text" strokeWidth={2} />
      <span className="min-w-0 flex-1 truncate text-[15px] text-text">{label}</span>
      {badge ? <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1.5 text-[11px] font-bold text-white">{badge > 9 ? "9+" : badge}</span> : null}
      {detail ? <span className="text-sm tabular-nums text-muted">{detail}</span> : null}
      <ChevronRight className="h-4 w-4 shrink-0 text-muted" strokeWidth={2.25} />
    </>
  );
  const className = "flex min-h-[3.25rem] w-full items-center gap-3.5 border-t border-border px-4 text-left first:border-t-0 active:bg-text/[.06]";
  return to ? <Link to={to} className={className}>{body}</Link> : <button type="button" onClick={onClick} className={className}>{body}</button>;
}

function ClearXpHistoryDialog({ onConfirm, onDismiss }: { onConfirm: () => void; onDismiss: () => void }) {
  const { t } = useTranslation();
  return (
    <Modal onDismiss={onDismiss}>
      <h2 className="text-base font-bold text-text">{t("profile.xpHistoryClearConfirmTitle")}</h2>
      <p className="mt-2 select-text text-sm leading-relaxed text-muted">{t("profile.xpHistoryClearConfirmMessage")}</p>
      <div className="mt-5 flex justify-end gap-2">
        <button onClick={onDismiss} className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted transition-colors hover:bg-text/[.06]">{t("common.cancel")}</button>
        <button onClick={onConfirm} className="rounded-lg bg-rose-500 px-3.5 py-2 text-sm font-bold text-text transition-opacity hover:opacity-90">{t("profile.xpHistoryClear")}</button>
      </div>
    </Modal>
  );
}

// Matches ProfilePage's own layout proportions (same header/stat-row/two-column grid shape) so
// flipping over to the real content doesn't visibly reflow - same reasoning as AnimeCard's own
// SkeletonCard.
function ProfileSkeleton() {
  return (
    <div className="min-h-full animate-pulse bg-app-bg pb-16">
      <div className="border-b border-border px-8 pb-8 pt-10 mobile:px-4">
        <div className="flex items-center gap-5">
          <div className="h-20 w-20 shrink-0 rounded-full bg-text/[.06]" />
          <div className="min-w-0 flex-1">
            <div className="h-7 w-48 rounded bg-text/[.08]" />
            <div className="mt-3 h-1.5 w-full max-w-xs rounded-full bg-text/[.06]" />
          </div>
        </div>
      </div>
      <div className="grid max-w-6xl grid-cols-1 gap-8 px-8 pt-8 lg:grid-cols-3 2xl:max-w-[1600px] 2xl:grid-cols-4 mobile:px-4">
        <div className="space-y-8 lg:col-span-2">
          <div className="grid grid-cols-5 gap-4 mobile:grid-cols-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="rounded-2xl border border-border bg-text/[.03] p-4">
                <div className="h-5 w-5 rounded bg-text/[.08]" />
                <div className="mt-3 h-6 w-10 rounded bg-text/[.08]" />
                <div className="mt-2 h-3 w-16 rounded bg-text/[.06]" />
              </div>
            ))}
          </div>
          <div>
            <div className="mb-4 h-4 w-32 rounded bg-text/[.08]" />
            <div className="flex h-36 items-end gap-1.5">
              {Array.from({ length: ACTIVITY_DAYS }).map((_, i) => (
                <div key={i} className="flex-1 rounded-sm bg-text/[.06]" style={{ height: `${8 + ((i * 37) % 80)}%` }} />
              ))}
            </div>
          </div>
          <div>
            <div className="mb-3 h-4 w-28 rounded bg-text/[.08]" />
            <div className="flex flex-col gap-1">
              {Array.from({ length: RECENT_LIMIT }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 p-2">
                  <div className="h-14 w-10 shrink-0 rounded-lg bg-text/[.06]" />
                  <div className="min-w-0 flex-1"><div className="h-4 w-2/3 rounded bg-text/[.06]" /></div>
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="grid grid-cols-1 gap-8 lg:col-span-1 2xl:col-span-2 2xl:grid-cols-2">
          <div>
            <div className="mb-3 h-4 w-32 rounded bg-text/[.08]" />
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-[70px] rounded-xl border border-border bg-text/[.02]" />
              ))}
            </div>
          </div>
          <div>
            <div className="mb-3 h-4 w-24 rounded bg-text/[.08]" />
            <div className="flex flex-col gap-1">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 p-2">
                  <div className="h-9 w-9 shrink-0 rounded-lg bg-text/[.06]" />
                  <div className="min-w-0 flex-1"><div className="h-4 w-1/2 rounded bg-text/[.06]" /></div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ProfileHeader({
  name,
  onNameChange,
  avatarDataUrl,
  onAvatarChange,
  bannerFilename,
  onBannerChange,
  levelProgress,
  streak,
  playStreakOnMount,
}: {
  name: string;
  onNameChange: (name: string | null) => void;
  avatarDataUrl: string | null;
  onAvatarChange: (dataUrl: string | null) => void;
  bannerFilename: string | null;
  onBannerChange: (filename: string | null) => void;
  levelProgress: LevelProgress;
  streak: StreakInfo;
  playStreakOnMount: boolean;
}) {
  return (
    <div className="group/header relative flex min-h-[176px] items-center overflow-hidden border-b border-border">
      <BannerMedia filename={bannerFilename} />
      {/* The banner's own darkening only guarantees contrast for light text on it - forced here
          via the same CSS vars text-text/text-muted read from, regardless of which theme (light
          or dark) is actually active, since a light theme's near-black text would otherwise
          vanish into that overlay. Centered vertically in the banner (the outer flex above) rather
          than pinned to its bottom - a taller banner used to leave the avatar/name hugging the
          bottom edge with a lot of dead space above them instead of sitting in the middle of it. */}
      <div
        className="relative flex w-full items-center gap-5 px-8 py-8"
        style={bannerFilename ? ({ "--color-text": "244 244 245", "--color-muted": "161 161 170" } as React.CSSProperties) : undefined}
      >
        <AvatarPicker avatarDataUrl={avatarDataUrl} onChange={onAvatarChange} />
        <div className="min-w-0 flex-1">
          <NameEditor name={name} onChange={onNameChange} streak={streak} playStreakOnMount={playStreakOnMount} />
          <LevelBar levelProgress={levelProgress} />
        </div>
      </div>
      {/* Painted last, after the row above, purely so its buttons win the hit-test over that row's
          own box (an absolutely-positioned sibling earlier in the DOM loses that fight even where
          the later one is visually empty) - see BannerActions for why the wrapper itself has to
          stay click-through. */}
      <BannerActions filename={bannerFilename} onChange={onBannerChange} />
    </div>
  );
}

// Kept in sync with EXTENSION_BY_MIME in main/ipc/profileBanner.ts, which is what actually
// enforces this - this is just what keeps the OS picker (and the guard right below it) from
// offering something the main process would only reject anyway.
const ACCEPTED_BANNER_TYPES = new Set(["image/gif", "image/png", "image/jpeg", "image/webp", "video/mp4"]);

// A still image, a GIF, or a short muted/looping video behind the whole header - name, level bar
// and all - the way a streaming profile's cover art sits behind everything rather than as its own
// separate strip. Darkened so the text on top of it stays readable regardless of what's under it.
function BannerMedia({ filename }: { filename: string | null }) {
  if (!filename) return null;
  const isVideo = filename.toLowerCase().endsWith(".mp4");
  return (
    <div className="absolute inset-0">
      {isVideo ? (
        <video key={filename} src={profileBannerUrl(filename)} className="h-full w-full object-cover" autoPlay loop muted playsInline />
      ) : (
        <img key={filename} src={profileBannerUrl(filename)} alt="" className="h-full w-full object-cover" />
      )}
      {/* Darkens the art under it just enough that white text and icons read the same over any
          banner, bright or dark, still or moving. */}
      <div className="absolute inset-0 bg-black/55" />
    </div>
  );
}

// The banner's edit/remove buttons, kept in their own layer on top of everything else in the
// header (see the comment where this is mounted) - `pointer-events-none` on the layer itself so
// the empty space around the two buttons still passes clicks through to whatever is actually
// under it (the avatar, the name field), and `pointer-events-auto` puts it back just on them.
function BannerActions({ filename, onChange, inline }: { filename: string | null; onChange: (filename: string | null) => void; inline?: boolean }) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);

  const onPick = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    // The accept attribute already narrows the OS picker to these; this just guards a file
    // dragged in some other way, or renamed to slip past that filter.
    if (!file || !ACCEPTED_BANNER_TYPES.has(file.type)) return;
    setBusy(true);
    try {
      const bytes = await file.arrayBuffer();
      const savedFilename = await hibiki.profile.setBanner(bytes, file.type);
      onChange(savedFilename);
    } finally {
      setBusy(false);
    }
  };

  const onClear = async () => {
    await hibiki.profile.clearBanner();
    onChange(null);
  };

  // Phone: one round button among the header's corner buttons, so they cover as little of the
  // banner as possible - it picks a banner straight away, or with one already set opens a sheet to
  // change or remove it.
  if (inline) {
    return (
      <>
        <button
          type="button"
          onClick={() => (filename ? setSheetOpen(true) : inputRef.current?.click())}
          disabled={busy}
          aria-label={t("profile.editBanner")}
          className={MOBILE_CORNER_BUTTON}
        >
          <ImageUp className="h-[18px] w-[18px]" strokeWidth={2} />
        </button>
        <input ref={inputRef} type="file" accept={[...ACCEPTED_BANNER_TYPES].join(",")} onChange={onPick} className="hidden" />
        <BottomSheet open={sheetOpen} onClose={() => setSheetOpen(false)}>
          <SheetOption icon={<ImageUp className="h-5 w-5" strokeWidth={2} />} label={t("profile.editBanner")} onClick={() => { setSheetOpen(false); inputRef.current?.click(); }} />
          <SheetOption icon={<Trash2 className="h-5 w-5" strokeWidth={2} />} label={t("profile.removeBanner")} danger onClick={() => { setSheetOpen(false); void onClear(); }} />
        </BottomSheet>
      </>
    );
  }

  return (
    <div className="pointer-events-none absolute inset-0">
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={busy}
        aria-label={t("profile.editBanner")}
        title={t("profile.editBanner")}
        className="pointer-events-auto absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-lg bg-black/40 text-text opacity-0 transition-opacity hover:bg-black/60 group-hover/header:opacity-100 mobile:opacity-100"
      >
        <Pencil className="h-4 w-4" strokeWidth={2.25} />
      </button>
      {filename && (
        <button
          type="button"
          onClick={onClear}
          aria-label={t("profile.removeBanner")}
          title={t("profile.removeBanner")}
          className="pointer-events-auto absolute right-14 top-3 flex h-8 w-8 items-center justify-center rounded-lg bg-black/40 text-text opacity-0 transition-opacity hover:bg-black/60 group-hover/header:opacity-100 mobile:opacity-100"
        >
          <X className="h-4 w-4" strokeWidth={2.25} />
        </button>
      )}
      <input ref={inputRef} type="file" accept={[...ACCEPTED_BANNER_TYPES].join(",")} onChange={onPick} className="pointer-events-auto hidden" />
    </div>
  );
}

// A higher level is worth more visual weight, not just a bigger number - same idea as
// StreakBadge's own tiers (a full reskin of the chip: color, then a standing ring, then a slow
// color-cycle at the very top), applied here to both the level chip and the bar it fills.
interface LevelTier {
  min: number;
  bg: string;
  text: string;
  ring?: boolean;
  legendary?: boolean;
}
const LEVEL_TIERS: LevelTier[] = [
  { min: 50, bg: "rgba(217,70,239,.18)", text: "#e879f9", ring: true, legendary: true },
  { min: 35, bg: "rgba(234,179,8,.18)", text: "#facc15", ring: true },
  { min: 20, bg: "rgba(168,85,247,.16)", text: "#c084fc" },
  { min: 10, bg: "rgba(56,189,248,.16)", text: "#38bdf8" },
  { min: 5, bg: "rgba(239,68,68,.16)", text: "#f87171" },
  // Below the first real tier, this just tracks the app's own accent color instead of a fixed
  // hue - the chip looks exactly like it always did until leveling actually earns it a color.
  { min: 1, bg: "rgb(var(--color-accent) / .15)", text: "rgb(var(--color-accent-text))" },
];
function levelTierFor(level: number): LevelTier {
  return LEVEL_TIERS.find((tier) => level >= tier.min)!;
}

function LevelBar({ levelProgress }: { levelProgress: LevelProgress }) {
  const { t } = useTranslation();
  const percent = Math.min(100, (levelProgress.xpIntoLevel / levelProgress.xpForLevel) * 100);
  const tier = levelTierFor(levelProgress.level);
  return (
    <div className="mt-2.5 flex max-w-xs items-center gap-2.5 mobile:mt-0 mobile:max-w-none">
      <span
        className={cn("relative shrink-0 rounded-md px-2 py-0.5 text-xs font-bold transition-colors duration-500", tier.legendary && "legendary-glow")}
        style={{ background: tier.bg, color: tier.text }}
      >
        {tier.ring && <span className="pointer-events-none absolute -inset-[3px] rounded-md" style={{ border: `1.5px solid ${tier.text}`, opacity: 0.7 }} />}
        {t("profile.levelBadge", { level: levelProgress.level })}
      </span>
      <div className="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-text/[.08]">
        <div
          className="h-full rounded-full ring-1 ring-inset ring-border transition-[width,background-color] duration-500"
          style={{ width: `${percent}%`, background: tier.text, boxShadow: tier.min >= 35 ? `0 0 8px ${tier.text}60` : undefined }}
        />
      </div>
      <span className="shrink-0 text-[11px] tabular-nums text-muted">{levelProgress.xpIntoLevel}/{levelProgress.xpForLevel} {t("profile.xp")}</span>
    </div>
  );
}

// A real canvas 2D resample (imageSmoothingQuality: "high"), not just a plain <img> - AnimeCard's
// own SmoothImage dropped exactly this technique (see that file) because doing it per-poster made
// a whole scrolling catalog of hundreds of cards expensive to keep smooth. None of that applies to
// a single always-mounted avatar, so there's no reason not to still do it here - this is a picked
// user file of whatever size/quality they happened to have, unlike a source's own catalog artwork,
// so it's much more likely to actually need real resampling instead of just being displayed at
// close to its native resolution already.
function ResampledAvatar({ src }: { src: string }) {
  const imageRef = useRef<HTMLImageElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [loadedSrc, setLoadedSrc] = useState("");

  useEffect(() => {
    const img = imageRef.current;
    const canvas = canvasRef.current;
    if (!img || !canvas) return;
    let frame = 0;
    const draw = () => {
      canvas.style.opacity = "0";
      if (!img.complete || !img.naturalWidth || !img.naturalHeight) return;
      const width = img.clientWidth;
      const height = img.clientHeight;
      if (!width || !height) return;
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.round(width * ratio));
      canvas.height = Math.max(1, Math.round(height * ratio));
      const context = canvas.getContext("2d");
      if (!context) return;
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = "high";
      // Centered object-cover, matching the fallback <img> below without stretching it - this
      // still runs (unlike SmoothImage's old upscale bail-out) even when the picked file is
      // smaller than the 80x80 slot: canvas interpolation can't invent missing detail, but it
      // still smooths the transition between source pixels noticeably better than a plain <img>
      // occasionally does for a `data:` URL at an odd scale ratio.
      const scale = Math.max(width / img.naturalWidth, height / img.naturalHeight);
      const sourceWidth = width / scale;
      const sourceHeight = height / scale;
      context.drawImage(img,
        (img.naturalWidth - sourceWidth) / 2, (img.naturalHeight - sourceHeight) / 2,
        sourceWidth, sourceHeight, 0, 0, canvas.width, canvas.height);
      canvas.style.opacity = "1";
    };
    const schedule = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(draw);
    };
    const observer = new ResizeObserver(schedule);
    observer.observe(img);
    window.addEventListener("resize", schedule);
    let density: MediaQueryList;
    const trackDensity = () => {
      density?.removeEventListener("change", trackDensity);
      density = matchMedia(`(resolution: ${window.devicePixelRatio}dppx)`);
      density.addEventListener("change", trackDensity);
      schedule();
    };
    trackDensity();
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      window.removeEventListener("resize", schedule);
      density.removeEventListener("change", trackDensity);
    };
  }, [src, loadedSrc]);

  return (
    <span className="relative block h-full w-full overflow-hidden">
      <img
        ref={imageRef}
        src={src}
        alt=""
        className="h-full w-full object-cover"
        onLoad={(event) => setLoadedSrc(event.currentTarget.currentSrc)}
      />
      <canvas key={src} ref={canvasRef} aria-hidden="true" className="pointer-events-none absolute inset-0 h-full w-full" style={{ opacity: 0 }} />
    </span>
  );
}

function AvatarPicker({ avatarDataUrl, onChange }: { avatarDataUrl: string | null; onChange: (dataUrl: string | null) => void }) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const onPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => onChange(typeof reader.result === "string" ? reader.result : null);
    reader.readAsDataURL(file);
  };
  return (
    <button onClick={() => inputRef.current?.click()} aria-label={t("profile.editAvatar")} className="group relative h-20 w-20 shrink-0 overflow-hidden rounded-full bg-text/[.06] ring-2 ring-border">
      {avatarDataUrl ? <ResampledAvatar src={avatarDataUrl} /> : <div className="flex h-full w-full items-center justify-center"><User className="h-8 w-8 text-muted" strokeWidth={1.5} /></div>}
      <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
        <Pencil className="h-5 w-5 text-text" strokeWidth={2} />
      </div>
      <input ref={inputRef} type="file" accept="image/*" onChange={onPick} className="hidden" />
    </button>
  );
}

function NameEditor({ name, onChange, streak, playStreakOnMount }: { name: string; onChange: (name: string | null) => void; streak: StreakInfo; playStreakOnMount: boolean }) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(name);

  const commit = () => {
    const trimmed = draft.trim();
    onChange(trimmed.length > 0 ? trimmed : null);
    setEditing(false);
  };

  if (editing) {
    return (
      <div className="flex items-center gap-2">
        <input
          autoFocus
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") commit(); if (e.key === "Escape") { setDraft(name); setEditing(false); } }}
          onBlur={commit}
          className="h-10 w-full max-w-xs rounded-lg bg-text/[.06] px-3 text-xl font-bold text-text outline-none ring-1 ring-accent/50"
        />
        <button onClick={commit} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-accent text-accent-fg"><Check className="h-4 w-4" strokeWidth={2.5} /></button>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-2.5">
      <StreakBadge current={streak.current} best={streak.best} atRisk={streak.atRisk} playOnMount={playStreakOnMount} />
      <button onClick={() => { setDraft(name); setEditing(true); }} aria-label={t("profile.editName")} className="group flex min-w-0 items-center gap-2">
        <h1 className="select-text truncate text-2xl font-bold text-text">{name}</h1>
        <Pencil className="h-4 w-4 shrink-0 text-muted/70 opacity-0 transition-opacity group-hover:opacity-100 mobile:opacity-100" strokeWidth={2} />
      </button>
    </div>
  );
}

function StatCard({ icon: Icon, label, value, sub }: { icon: typeof Film; label: string; value: string | number; sub?: string }) {
  // A short number ("12", "3.2 ч") reads fine at text-2xl, but a longer value with a unit
  // ("0.5 эп./нед.") wraps onto two lines at that size and ends up looking oversized/cramped -
  // five narrower columns (since the pace card joined the row) made this a lot more likely to bite.
  const isLong = String(value).length > 6;
  return (
    <div className="rounded-2xl border border-border bg-text/[.03] p-4">
      <Icon className="h-5 w-5 text-accent-text" strokeWidth={2} />
      <p className={cn("mt-3 truncate font-bold text-text", isLong ? "text-lg" : "text-2xl")}>{value}</p>
      <p className="mt-0.5 text-xs text-muted">{label}</p>
      {sub && <p className="mt-1 text-[11px] text-muted/70">{sub}</p>}
    </div>
  );
}

function RecentRow({ entry, locale }: { entry: LibraryEntry; locale: string }) {
  const { t } = useTranslation();
  const title = animeTitle(entry.anime);
  const Icon = LIBRARY_CATEGORY_ICONS[entry.category];
  return (
    <Link to="/anime/$sourceId/$animeId" params={{ sourceId: entry.sourceId, animeId: entry.animeId }} className="flex items-center gap-3 rounded-xl p-2 transition-colors hover:bg-text/[.05]">
      <div className="h-14 w-10 shrink-0 overflow-hidden rounded-lg bg-surface ring-1 ring-border">
        {entry.anime.posterUrl && <img src={entry.anime.posterUrl} alt="" loading="lazy" className="h-full w-full object-cover" />}
      </div>
      <div className="min-w-0 flex-1">
        <p className="select-text truncate text-sm font-semibold text-text">{title}</p>
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
          <Icon className="h-3 w-3 shrink-0" strokeWidth={2} />
          <span>{t(LIBRARY_CATEGORY_LABEL_KEYS[entry.category])}</span>
        </div>
      </div>
      <span className="shrink-0 text-xs text-muted/70">{relativeDate(entry.addedAt, t, locale)}</span>
    </Link>
  );
}

function XpHistoryRow({ event, locale }: { event: XpEvent; locale: string }) {
  const { t } = useTranslation();
  const tier = ACHIEVEMENT_TIER_BY_ID[event.kind];
  const Icon = tier?.icon ?? Sparkles;
  return (
    <div className="flex items-center gap-3 rounded-xl p-2">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-text/[.06] text-muted">
        <Icon className="h-4 w-4" strokeWidth={2} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-text">{tier ? t(`profile.achievements.${tier.id}.title`) : event.kind}</p>
        <span className="text-xs text-muted/70">{relativeDate(event.createdAt, t, locale)}</span>
      </div>
      <span className="shrink-0 text-[11px] font-semibold text-accent-text/80">+{event.xp} {t("profile.xp")}</span>
    </div>
  );
}

const WEEK = 7;

/** "15-21 Sep", or "29 Sep - 5 Oct" across months. */
function dayRange(fromKey: string, toKey: string, locale: string): string {
  const toDate = (key: string) => {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, m - 1, d);
  };
  try {
    return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" }).formatRange(toDate(fromKey), toDate(toKey));
  } catch {
    return `${shortDayLabel(fromKey, locale)} - ${shortDayLabel(toKey, locale)}`;
  }
}

/**
 * The phone's activity chart: a week at a time, today at the right edge, and swiped right for earlier
 * weeks back to the start of the series. A tap on a day puts its numbers in the header, which hover
 * did on desktop.
 */
function MobileActivityBars({ series, locale }: { series: DailyActivity[]; locale: string }) {
  const { t } = useTranslation();
  const scroller = useRef<HTMLDivElement>(null);
  const [picked, setPicked] = useState<number | null>(null);
  // By time watched: a day of half-watched episodes is activity too, and counted no episodes.
  const max = Math.max(1, ...series.map((d) => d.watchedMs));
  const pickedDay = picked !== null ? series[picked] : null;
  // The first day in view, for the header's "15-21 Sep" while no day is picked.
  const [firstShown, setFirstShown] = useState(Math.max(0, series.length - WEEK));

  // Opens on the latest week.
  useEffect(() => {
    const el = scroller.current;
    if (el) el.scrollLeft = el.scrollWidth;
    setFirstShown(Math.max(0, series.length - WEEK));
  }, [series.length]);

  const onScroll = () => {
    const el = scroller.current;
    if (!el || el.scrollWidth <= el.clientWidth) return;
    const column = el.scrollWidth / series.length;
    setFirstShown(Math.min(Math.max(0, series.length - WEEK), Math.max(0, Math.round(el.scrollLeft / column))));
  };
  const shownFrom = series[firstShown];
  const shownTo = series[Math.min(series.length - 1, firstShown + WEEK - 1)];

  const dateOf = (key: string) => {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, m - 1, d);
  };
  const last = series.length - 1;

  return (
    <div>
      <div className="mb-3 flex min-h-[1.25rem] items-baseline justify-between gap-3">
        <h2 className="text-sm font-bold text-text">{t("profile.activityTitleShort")}</h2>
        {pickedDay ? (
          <span className="truncate text-xs text-muted">
            {shortDayLabel(pickedDay.date, locale)} · {t("common.episodesShort", { count: pickedDay.completedCount })} · {t("profile.activityMinutesShort", { count: Math.round(pickedDay.watchedMs / 60_000) })}
          </span>
        ) : (
          shownFrom && shownTo && <span className="truncate text-xs text-muted/70">{dayRange(shownFrom.date, shownTo.date, locale)}</span>
        )}
      </div>
      <div
        ref={scroller}
        onScroll={onScroll}
        className="no-scrollbar grid snap-x snap-mandatory grid-flow-col gap-1.5 overflow-x-auto overscroll-x-contain"
        style={{ gridAutoColumns: `calc((100% - ${WEEK - 1} * 0.375rem) / ${WEEK})` }}
      >
        {series.map((d, i) => {
          const date = dateOf(d.date);
          const active = picked === i;
          // Snap by weeks counted from today, so a swipe lands on whole weeks ending on today's weekday.
          const snap = (last - i) % WEEK === WEEK - 1 || i === 0;
          return (
            <button
              key={d.date}
              type="button"
              onClick={() => setPicked(active ? null : i)}
              className={cn("flex flex-col items-stretch", snap && "snap-start")}
            >
              <div className="flex h-24 items-end px-1">
                <motion.div
                  initial={{ height: 0 }}
                  animate={{ height: d.watchedMs > 0 ? `${Math.max(8, (d.watchedMs / max) * 100)}%` : 4 }}
                  transition={{ duration: 0.35, ease: "easeOut" }}
                  // The same fixed coral as the desktop chart (see ActivityBars).
                  className={cn("w-full rounded-md", d.watchedMs === 0 && (active ? "bg-text/[.18]" : "bg-text/[.08]"))}
                  style={d.watchedMs > 0 ? { backgroundColor: active ? "#FF7A86CC" : "#FF7A86" } : undefined}
                />
              </div>
              <span className={cn("mt-2 text-[11px] leading-tight", i === last ? "font-bold text-text" : "text-muted/80")}>
                {date.toLocaleDateString(locale, { weekday: "short" })}
              </span>
              <span className={cn("text-[11px] leading-tight", i === last ? "font-semibold text-text" : "text-muted/60")}>{date.getDate()}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ActivityBars({ series, locale }: { series: DailyActivity[]; locale: string }) {
  const { t } = useTranslation();
  const [hovered, setHovered] = useState<number | null>(null);
  const max = Math.max(1, ...series.map((d) => d.completedCount));
  const hoveredDay = hovered !== null ? series[hovered] : null;
  return (
    <div>
      <div className="relative">
        {hoveredDay && (
          <div
            className="pointer-events-none absolute bottom-full z-10 mb-2 w-max -translate-x-1/2 select-text rounded-lg border border-border bg-surface px-3 py-2 text-xs shadow-2xl"
            style={{ left: `${((hovered! + 0.5) / series.length) * 100}%` }}
          >
            <p className="font-semibold text-text">{shortDayLabel(hoveredDay.date, locale)}</p>
            <p className="mt-1 text-muted">{t("profile.activityEpisodes", { count: hoveredDay.completedCount })}</p>
            <p className="text-muted">{t("profile.activityMinutes", { count: Math.round(hoveredDay.watchedMs / 60_000) })}</p>
          </div>
        )}
        <div className="flex h-36 items-end gap-1.5 mobile:h-24 mobile:gap-1">
          {series.map((d, i) => (
            <motion.div
              key={d.date}
              initial={{ height: 0 }}
              animate={{ height: d.completedCount > 0 ? `${Math.max(8, (d.completedCount / max) * 100)}%` : 4 }}
              transition={{ duration: 0.35, ease: "easeOut", delay: i * 0.008 }}
              onMouseEnter={() => setHovered(i)}
              onMouseLeave={() => setHovered((h) => (h === i ? null : h))}
              // A fixed coral (#FF7A86, matching the Android app's own ActivityBarChart - see
              // LocalProfileAnalyticsSection.kt), not bg-accent - sidesteps the accent-vs
              // -background contrast problem entirely (a white or black accent color would
              // otherwise blend straight into a light or dark page background) and keeps this
              // reading the same across both apps regardless of whatever accent is picked here.
              className={cn("flex-1 rounded-sm transition-colors", d.completedCount === 0 && (hovered === i ? "bg-text/[.16]" : "bg-text/[.08]"))}
              style={d.completedCount > 0 ? { backgroundColor: hovered === i ? "#FF7A86CC" : "#FF7A86" } : undefined}
            />
          ))}
        </div>
      </div>
      <div className="mt-2 flex gap-1.5 mobile:gap-1">
        {series.map((d, i) => (
          <div key={d.date} className="flex-1 text-center text-[10px] text-muted/70">
            {(i % 5 === 0 || i === series.length - 1) ? shortDayLabel(d.date, locale) : ""}
          </div>
        ))}
      </div>
    </div>
  );
}
