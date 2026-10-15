import { Children, useMemo } from "react";
import { Link } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PullToRefresh } from "@/components/PullToRefresh";
import { useTranslation } from "react-i18next";
import { Play, Radio, RefreshCw, ShieldAlert, WifiOff } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { AnimeCard, PosterGrid, PosterGridSkeleton, PosterRow, PosterRowSkeleton, animeTitle } from "@/components/AnimeCard";
import { ContinueWatchingFrameRow } from "@/components/ContinueWatchingRow";
import { ErrorBanner } from "@/components/ErrorBanner";
import { CloudflareCheckButton, useCloudflareText } from "@/components/CloudflareCheck";
import { HERO_ACTION_CLASS, HeroCarousel, type HeroSlide } from "@/components/Hero";
import { MobileHero } from "@/components/MobileHero";
import { MobileSearchButton } from "@/components/MobileSearchButton";
import { isMobile } from "@/lib/mobile";
import { cn } from "@/lib/cn";
import { useContinueWatching } from "@/lib/continueWatching";
import { useUiStore } from "@/stores/uiStore";
import { pickRelevanceSort } from "@/lib/catalogSort";
import { isCellular, isDataSaverActive } from "@/lib/connectionQuality";
import { useNetworkPrefsStore, type HomeRefreshMode } from "@/stores/networkPrefsStore";
import type { AnimeTitle } from "@shared/types";
import { readPersisted, useFilterCatalog, usePersistedQueryOptions, writePersisted } from "@/lib/persistedQuery";

const RECOMMENDED_COUNT = 20;
const POOL_WINDOW = 24;
// The pool window is fetched starting at a random offset into the source's "popular" ranking
// (0..MAX_POOL_OFFSET), so "you might like" pulls from a different slice of the catalog each
// visit instead of always reshuffling the same top N titles.
const MAX_POOL_OFFSET = 100;
const HERO_SLIDE_COUNT = 5;
// A genre row only earns its place if enough of the pool actually shares a genre - otherwise
// it'd just be a near-duplicate of "you might like" with 2-3 items.
const MIN_GENRE_MATCHES = 4;

const HOUR_MS = 60 * 60 * 1000;
/** How long the home rows saved on disk stand in for the source without asking it again, per the
 * "Home refresh" setting (Settings > Network). The popular ranking barely moves in a few days;
 * "Latest releases" exists to show what came out today, so it never waits longer than half a day
 * unless the user chose manual refresh. Pull-to-refresh always gets a fresh home. */
const HOME_FRESH_MS: Record<HomeRefreshMode, { popular: number; latest: number }> = {
  launch: { popular: 10 * 60 * 1000, latest: 10 * 60 * 1000 },
  day: { popular: 24 * HOUR_MS, latest: 12 * HOUR_MS },
  "3days": { popular: 72 * HOUR_MS, latest: 12 * HOUR_MS },
  manual: { popular: Infinity, latest: Infinity },
};
/** On mobile data or a slow connection saved rows are kept at least this long. */
const SAVER_FRESH_MS = 7 * 24 * HOUR_MS;

function homeFreshFor(row: "popular" | "latest", mode: HomeRefreshMode): number {
  const normal = HOME_FRESH_MS[mode][row];
  return isCellular() || isDataSaverActive() ? Math.max(normal, SAVER_FRESH_MS) : normal;
}

interface SavedPool {
  offset: number;
  titles: AnimeTitle[];
}

const POOL_OFFSET_KEY = "hibiki-home-pool-offset";

// On a slow connection (or with data saver on) the window stays where it was last time: the same
// titles, whose posters are already on disk, instead of two dozen new ones to download before the
// page is whole. Variety waits for a better network.
function poolOffsetFor(sourceId: string | undefined): number {
  let saved: Record<string, number> = {};
  try {
    saved = JSON.parse(localStorage.getItem(POOL_OFFSET_KEY) ?? "{}") as Record<string, number>;
  } catch {
    // Unreadable: a fresh window, as always.
  }
  const previous = sourceId ? saved[sourceId] : undefined;
  if (typeof previous === "number" && isDataSaverActive()) return previous;
  const offset = Math.floor(Math.random() * (MAX_POOL_OFFSET + 1));
  if (sourceId) {
    try {
      localStorage.setItem(POOL_OFFSET_KEY, JSON.stringify({ ...saved, [sourceId]: offset }));
    } catch {
      // Not remembered: the next slow start just gets a new window too.
    }
  }
  return offset;
}

// Fisher-Yates - picked once per fetched pool (see the useMemo below), not on every render, so
// the grid doesn't jumble itself while the user is looking at it.
function shuffled<T>(items: T[]): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

export function CatalogPage() {
  const { t } = useTranslation();
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const source = sources.data?.find((s) => s.id === activeSourceId) ?? sources.data?.[0];
  // The source's own best-known order, once we know what it offers (undefined = its default listing).
  const settings = useFilterCatalog(source?.id);
  // A user-picked override (Settings > Home) wins whenever the current source still actually offers
  // that sort id - a source can change its own sort ids between versions, and silently falling back
  // to the auto-guess is safer than sending a now-nonexistent id to the source.
  const homeSortOverride = useUiStore((s) => (source ? s.homeSortBySource[source.id] : undefined));
  const sortOptions = settings.data?.sortOptions ?? [];
  const sortMode = (homeSortOverride && sortOptions.some((o) => o.id === homeSortOverride))
    ? homeSortOverride
    : pickRelevanceSort(sortOptions);
  // A saved catalog counts: it is what the source answered last time, and waiting for a refetch
  // would put the old network round trip back in front of every row.
  const homeRefresh = useNetworkPrefsStore((st) => st.homeRefresh);
  const settingsReady = settings.isFetched || settings.data !== undefined;
  const heroPersisted = usePersistedQueryOptions("hero", source?.id, sortMode ?? "", () => hibiki.sources.search(source!.id, { limit: HERO_SLIDE_COUNT, sort: sortMode }));
  const hero = useQuery({
    queryKey: ["hero", source?.id, sortMode ?? ""],
    enabled: !!source && settingsReady,
    staleTime: homeFreshFor("popular", homeRefresh),
    ...heroPersisted,
  });
  // Compute the source's window in the same render that enables the query. Keeping this in state
  // and replacing it from an effect after `source` arrived let React Query start one request with
  // the old offset and then immediately start a second with the new one.
  // A pool saved on disk a few minutes ago keeps its window: a new random offset would only throw
  // away a row that is already here and download another.
  const savedPool = useMemo(() => readPersisted<SavedPool>("pool", source?.id, sortMode ?? ""), [source?.id, sortMode]);
  const poolOffset = useMemo(
    () => (savedPool && Date.now() - savedPool.savedAt < homeFreshFor("popular", homeRefresh) ? savedPool.data.offset : poolOffsetFor(source?.id)),
    // Picked once per source, as before - not again when the sort arrives.
    [source?.id],
  );
  const poolSeed = savedPool?.data.offset === poolOffset ? savedPool : undefined;
  const pool = useQuery({
    queryKey: ["popular-pool", source?.id, poolOffset, sortMode ?? ""],
    enabled: !!source && settingsReady,
    staleTime: homeFreshFor("popular", homeRefresh),
    initialData: poolSeed?.data.titles,
    initialDataUpdatedAt: poolSeed?.savedAt,
    queryFn: async () => {
      let window = await hibiki.sources.search(source!.id, { offset: poolOffset, limit: POOL_WINDOW, sort: sortMode });
      // A short catalog can have fewer titles than our random offset - fall back to the start
      // of the ranking rather than showing an empty section.
      if (window.length === 0 && poolOffset !== 0) window = await hibiki.sources.search(source!.id, { offset: 0, limit: POOL_WINDOW, sort: sortMode });
      writePersisted<SavedPool>("pool", source!.id, sortMode ?? "", { offset: poolOffset, titles: window });
      return window;
    },
  });
  // What just came out on the source, as the Android app's home had it - for the sources that keep
  // such a list (LATEST_RELEASES). A failure only hides the row: the popular row speaks for the source.
  const hasLatest = source?.capabilities.includes("LATEST_RELEASES") ?? false;
  const latestPersisted = usePersistedQueryOptions("latest", source?.id, "", () => hibiki.sources.latest(source!.id, RECOMMENDED_COUNT));
  const latest = useQuery({
    queryKey: ["latest", source?.id],
    enabled: !!source && hasLatest,
    staleTime: homeFreshFor("latest", homeRefresh),
    ...latestPersisted,
  });
  // What was watched on this source, turned into what to watch next on it (core/recommendations).
  // Only asked once there is history; the backend reuses its answer until the history changes.
  const { hasHistory, slots: continueSlots } = useContinueWatching();
  // The row drops titles it could not resolve (no source here, no cached card); with none left its
  // heading alone would sit over nothing.
  const hasContinueCards = continueSlots.some((slot) => slot.anime !== null);
  const recommendations = useQuery({
    queryKey: ["recommendations", source?.id, sortMode ?? ""],
    enabled: !!source && settingsReady && hasHistory,
    queryFn: () => hibiki.sources.recommendations(source!.id, sortMode),
    staleTime: 5 * 60 * 1000,
  });
  const picks = recommendations.data?.picks ?? [];
  const continuations = recommendations.data?.continuations ?? [];
  // Shared with the profile page's own "continue watching" row - see useContinueWatching, which
  // caches per-title lookups under query keys both pages agree on so whichever loads first does
  // the actual work.
  const heroSlides = useMemo(() => hero.data ?? [], [hero.data]);
  const poolTitles = useMemo(() => pool.data ?? [], [pool.data]);
  const isNew = !hasHistory;
  const sourceById = useMemo(() => new Map((sources.data ?? []).map((s) => [s.id, s])), [sources.data]);
  // Re-shuffled each time a fresh pool comes in (new source, new random offset, ...) so this
  // section doesn't always show the same titles in the same order.
  const recommended = useMemo(() => shuffled(poolTitles).slice(0, RECOMMENDED_COUNT), [poolTitles]);
  // Most common genre in the current pool, with at least MIN_GENRE_MATCHES titles sharing it -
  // gives a themed row using only data we already fetched, no extra request.
  const genreSection = useMemo(() => {
    const items = poolTitles;
    const counts = new Map<string, number>();
    for (const item of items) for (const genre of item.genres ?? []) counts.set(genre, (counts.get(genre) ?? 0) + 1);
    let topGenre: string | null = null; let topCount = 0;
    for (const [genre, count] of counts) if (count > topCount) { topGenre = genre; topCount = count; }
    if (!topGenre || topCount < MIN_GENRE_MATCHES) return null;
    return { genre: topGenre, items: items.filter((item) => item.genres?.includes(topGenre!)) };
  }, [poolTitles]);
  const queryClient = useQueryClient();
  const refreshHome = () => Promise.all([
    sources.refetch(),
    // Only with a source: without one these have nothing to ask (and are switched off).
    ...(source ? [hero.refetch(), pool.refetch(), ...(hasLatest ? [latest.refetch()] : [])] : []),
    queryClient.invalidateQueries({ queryKey: ["recent-progress"] }),
    queryClient.invalidateQueries({ queryKey: ["recommendations"] }),
  ]);
  return <div className="relative min-h-full bg-app-bg pb-12">
    <PullToRefresh onRefresh={refreshHome} />
    {isMobile && <div className="absolute right-3 z-10" style={{ top: "calc(0.5rem + var(--safe-top))" }}><MobileSearchButton overArtwork /></div>}
    {sources.isLoading && <HeroSkeleton />}{sources.data?.length === 0 && <EmptySources />}{sources.isError && <ErrorBanner message={(sources.error as Error).message} className="m-8" />}
    {source && <>
      {/* Neither answered: the source is down or the phone is offline. One plain statement of that,
          instead of a missing hero over an empty section under a raw error. What is local - the
          continue-watching row - still shows below. (The previous visit's titles used to be painted
          from disk meanwhile; they were always different titles, swapped out a second later.) */}
      {hero.isError && pool.isError ? <SourceUnavailable name={source.name} error={hero.error} retrying={hero.isFetching || pool.isFetching} onRetry={() => void refreshHome()} /> : heroSlides.length > 0
        ? isMobile
          ? <MobileHero slides={heroSlides.map((slide) => toHeroSlide(slide, t("catalog.openTitle"), source.iconUrl))} label={t("catalog.trendingOnPrefix")} sourceName={source.name} />
          : <HeroCarousel slides={heroSlides.map((slide) => toHeroSlide(slide, t("catalog.openTitle"), source.iconUrl))} label={t("catalog.trendingOnPrefix")} sourceName={source.name} />
        : hero.isPending && <HeroSkeleton />}
      <div className="space-y-12 px-8 pt-10 mobile:space-y-8 mobile:px-4 mobile:pt-5">
        {pool.isError && !hero.isError && <ErrorBanner message={(pool.error as Error).message} error={pool.error} onRetry={() => void pool.refetch()} />}
        {/* Always the frame row: swapping to poster cards below a threshold meant the section
            changed shape as history filled up, and a single captured frame still reads as "here's
            where you left off" better than a poster does. */}
        {!isNew && hasContinueCards && <Section title={t("catalog.continueWatching")} action={t("catalog.viewHistory")} to="/history">
          <ContinueWatchingFrameRow sourceById={sourceById} />
        </Section>}
        {continuations.length > 0 && <Section title={t("catalog.continuations")}>
          <HomeTitles>{continuations.map(({ anime }) => <AnimeCard key={anime.id} anime={anime} />)}</HomeTitles>
        </Section>}
        {picks.length > 0 && <Section title={t("catalog.forYou", { source: source.name })}>
          <HomeTitles layout="grid">{picks.map(({ anime }) => <AnimeCard key={anime.id} anime={anime} />)}</HomeTitles>
        </Section>}
        {hasLatest && !latest.isError && (latest.isPending || (latest.data?.length ?? 0) > 0) && <Section title={t("catalog.latestReleases")}>
          {latest.isPending ? (isMobile ? <PosterRowSkeleton /> : <PosterGridSkeleton count={15} />) : <HomeTitles>{(latest.data ?? []).map(item => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</HomeTitles>}
        </Section>}
        {/* The pool is what is popular on the source, nothing more - personal picks are above. */}
        {!pool.isError && <Section title={t("catalog.popularNow")} action={t("catalog.openCatalog")} to="/catalog">
          {pool.isPending ? <PosterGridSkeleton count={isMobile ? MOBILE_GRID_COUNT : 15} /> : <HomeTitles layout="grid">{recommended.map(item => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</HomeTitles>}
        </Section>}
        {genreSection && <Section title={t("catalog.genreSection", { genre: genreSection.genre })} action={t("catalog.openCatalog")} to="/catalog">
          <HomeTitles layout="grid">{genreSection.items.map(item => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</HomeTitles>
        </Section>}
      </div>
    </>}
  </div>;
}
/** A source title as the shared carousel wants it - see HeroSlide for why this mapping exists at
 * all rather than the carousel taking an AnimeTitle. */
function toHeroSlide(anime: AnimeTitle, openLabel: string, sourceIconUrl?: string | null): HeroSlide {
  return {
    key: `${anime.sourceId}:${anime.id}`,
    sourceId: anime.sourceId,
    sourceIconUrl,
    title: animeTitle(anime),
    description: anime.description,
    posterUrl: anime.posterUrl,
    type: anime.type,
    year: anime.year,
    episodeCount: anime.availableEpisodeCount,
    genres: anime.genres,
    rating: anime.ratings?.[0] ? { value: anime.ratings[0].value, source: anime.ratings[0].source } : null,
    action: (
      <Link to="/anime/$sourceId/$animeId" params={{ sourceId: anime.sourceId, animeId: anime.id }} className={HERO_ACTION_CLASS}>
        <Play className="ml-0.5 h-4 w-4 fill-current" strokeWidth={0} />
        {openLabel}
      </Link>
    ),
  };
}

function Section({ title, action, to, children }: { title: string; action?: string; to?: "/catalog" | "/history"; children: React.ReactNode }) { return <section><div className="mb-5 flex items-center justify-between gap-3 mobile:mb-3"><h2 className="text-2xl font-bold tracking-[-.02em] text-text mobile:truncate mobile:text-[19px]">{title}</h2>{action && to && <Link to={to} className="text-sm font-semibold text-muted transition-colors hover:text-accent-text mobile:shrink-0 mobile:text-[13px]">{action} →</Link>}</div>{children}</section>; }
// A grid on desktop; on the phone a row the thumb scrolls sideways, so a section stays one screen-line tall.
// On the phone the two kinds of section read differently: a feed of what is new (latest episodes,
// continuations) is a row the thumb runs through, while a selection (for you, popular, a genre) is a
// 3x3 block the eye takes in at once - nine posters down the page, the rest a tap away in the catalog.
const MOBILE_GRID_COUNT = 9;
function HomeTitles({ layout = "row", children }: { layout?: "row" | "grid"; children: React.ReactNode }) {
  if (!isMobile) return <PosterGrid>{children}</PosterGrid>;
  if (layout === "row") return <PosterRow>{children}</PosterRow>;
  // Whole rows only: a fourth title alone under three looks like something failed to load.
  const items = Children.toArray(children);
  const count = items.length < 3 ? items.length : Math.min(MOBILE_GRID_COUNT, items.length - (items.length % 3));
  return <PosterGrid>{items.slice(0, count)}</PosterGrid>;
}
/**
 * The hero while its titles load, in the hero's own shape - the full-bleed slide with its text at
 * the bottom on the phone, the tall banner on the desktop - so nothing below moves when it arrives.
 * (Its query waits for the source's sort orders first, which on a slow source is most of the wait.)
 */
function HeroSkeleton() {
  if (isMobile) {
    return <div aria-hidden className="relative -mt-[var(--safe-top)] overflow-hidden" style={{ height: "calc(min(46vh, 420px) + var(--safe-top))", minHeight: 340 }}>
      <div className="skeleton absolute inset-0 opacity-60" />
      <div className="absolute inset-0" style={{ background: "linear-gradient(to bottom, transparent 30%, rgb(var(--color-bg) / 0.85) 80%, rgb(var(--color-bg)))" }} />
      <div className="absolute inset-x-0 bottom-0 px-4 pb-4">
        <div className="skeleton h-2.5 w-24 rounded-full" />
        <div className="skeleton mt-3 h-7 w-4/5 rounded-lg" />
        <div className="skeleton mt-2 h-7 w-1/2 rounded-lg" />
        <div className="skeleton mt-3 h-3 w-2/5 rounded-full" />
        <div className="mt-4 flex items-center justify-between">
          <div className="skeleton h-10 w-32 rounded-full" />
          <div className="flex gap-1.5"><div className="skeleton h-1 w-6 rounded-full" /><div className="skeleton h-1 w-1.5 rounded-full" /><div className="skeleton h-1 w-1.5 rounded-full" /></div>
        </div>
      </div>
    </div>;
  }
  return <div aria-hidden className="relative min-h-[520px] overflow-hidden border-b border-white/[.04] px-8 py-20">
    <div className="skeleton absolute inset-0 opacity-40" />
    <div className="relative flex min-h-[360px] max-w-2xl flex-col justify-end">
      <div className="skeleton h-3 w-40 rounded-full" />
      <div className="skeleton mt-5 h-12 w-[28rem] max-w-full rounded-xl" />
      <div className="skeleton mt-5 h-3 w-full max-w-lg rounded-full" />
      <div className="skeleton mt-2 h-3 w-4/5 max-w-lg rounded-full" />
      <div className="skeleton mt-8 h-11 w-40 rounded-xl" />
    </div>
  </div>;
}
function SourceUnavailable({ name, error, retrying, onRetry }: { name: string; error: unknown; retrying: boolean; onRetry: () => void }) {
  const { t } = useTranslation();
  const offline = typeof navigator !== "undefined" && !navigator.onLine;
  const cloudflare = useCloudflareText(error);
  return <div className="flex min-h-[340px] items-center justify-center px-8 py-16 mobile:min-h-[300px] mobile:px-6 mobile:pb-6 mobile:pt-[calc(4rem+var(--safe-top))]">
    <div className="max-w-sm text-center">
      <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]">{cloudflare ? <ShieldAlert className="h-6 w-6 text-muted" strokeWidth={1.75} /> : <WifiOff className="h-6 w-6 text-muted" strokeWidth={1.75} />}</div>
      <h1 className="text-xl font-bold text-text">{t("catalog.sourceUnavailableTitle", { source: name })}</h1>
      <p className="mt-3 text-sm leading-6 text-muted">{cloudflare ?? (offline ? t("common.offlineHint") : t("catalog.sourceUnavailableText"))}</p>
      <div className="mt-6 flex flex-wrap items-center justify-center gap-2">
        <CloudflareCheckButton error={error} className="rounded-xl bg-accent px-4 py-2.5 text-sm font-bold text-accent-fg transition-[filter] hover:brightness-110 mobile:rounded-full mobile:px-5" />
        <button onClick={onRetry} disabled={retrying} className="inline-flex items-center gap-2 rounded-xl bg-text/[.08] px-4 py-2.5 text-sm font-bold text-text transition-colors hover:bg-text/[.14] disabled:opacity-60 mobile:rounded-full mobile:px-5">
          <RefreshCw className={cn("h-4 w-4", retrying && "animate-spin")} strokeWidth={2} />{t("common.retry")}
        </button>
        {offline && <Link to="/downloads" className="rounded-xl px-4 py-2.5 text-sm font-semibold text-muted transition-colors hover:text-text mobile:rounded-full">{t("common.openDownloads")}</Link>}
      </div>
    </div>
  </div>;
}
function EmptySources() { const { t } = useTranslation(); return <div className="flex min-h-[calc(100vh-76px)] items-center justify-center p-8"><div className="max-w-sm text-center"><div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]"><Radio className="h-6 w-6 text-muted" strokeWidth={1.75} /></div><h1 className="text-xl font-bold text-text">{t("catalog.emptySourcesTitle")}</h1><p className="mt-3 text-sm leading-6 text-muted">{t("catalog.emptySourcesText")}</p><Link to="/sources" className="mt-6 inline-block rounded-xl bg-accent px-4 py-2.5 text-sm font-bold text-accent-fg">{t("catalog.openSources")}</Link></div></div>; }
