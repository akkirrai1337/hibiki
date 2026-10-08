import { memo } from "react";
import { SmoothImage } from "@/components/SmoothImage";
import { Link } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Radio, Trash2 } from "lucide-react";
import { cn } from "@/lib/cn";
import type { AnimeTitle } from "@shared/types";

export function animeTitle(anime: AnimeTitle) { return anime.russianName || anime.englishName || anime.originalName || anime.id; }

// Same pick Android makes for a compact rating label (LocalProfileSnapshot.kt: `ratings.firstOrNull()`)
// - a source orders its own ratings list with its native rating first (see e.g. yummy-anime.js's
// toRatings()), ahead of cross-referenced ones (MAL, Shikimori, ...), rather than us trying to pick
// "the best" source ourselves.
function formatRating(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

/** The one poster-grid rhythm used by Home, Catalog, and both search modes. Three columns on a phone. */
export function PosterGrid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-5 gap-x-4 gap-y-6 xl:grid-cols-6 mobile:grid-cols-3 mobile:gap-x-3 mobile:gap-y-5">{children}</div>;
}

/**
 * Phone only: posters in one row that scrolls sideways, running to the screen edges (the parent's
 * px-4 is undone and given back as scroll padding) with the next card peeking in.
 */
// Free scrolling with the system's own fling, no snapping: mandatory snap pulled every fling back to
// a card edge once it slowed, which read as the row jerking to a stop. overscroll-x-contain keeps a
// sideways swipe at the row's end from turning into the system's back gesture.
export function PosterRow({ children }: { children: React.ReactNode }) {
  return <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto overscroll-x-contain px-4 [&>*]:w-[30vw] [&>*]:max-w-[150px] [&>*]:shrink-0">{children}</div>;
}

export function PosterGridSkeleton({ count = 12 }: { count?: number }) {
  return <PosterGrid>{Array.from({ length: count }).map((_, index) => <SkeletonCard key={index} />)}</PosterGrid>;
}

/** The phone's sideways row, loading: the same card slots the row will hold. */
export function PosterRowSkeleton({ count = 4 }: { count?: number }) {
  return <PosterRow>{Array.from({ length: count }).map((_, index) => <SkeletonCard key={index} />)}</PosterRow>;
}

// Matches AnimeCard's own poster/title/meta proportions so a grid mixing loaded cards and
// still-loading slots doesn't visibly hitch when a skeleton flips over to the real thing.
export function SkeletonCard() {
  return <div>
    <div className="skeleton aspect-[2/3] rounded-xl" />
    <div className="skeleton mt-2.5 h-3.5 w-4/5 rounded" />
    <div className="skeleton mt-1.5 h-3 w-2/5 rounded" />
  </div>;
}

// Which source a title came from - only worth showing where a list can mix titles from several
// installed sources at once (continue watching, library), same as the Android app's
// AnimeSourceBadge; a single-source grid (catalog, search, popular-on-this-source) doesn't need it.
export interface AnimeCardSource {
  name: string;
  iconUrl?: string | null;
  // The extension this title came from is no longer installed (see knownSourcesStore) - the badge
  // still shows its remembered name/icon (same as Android's AnimeSourceBadge), but flags it with
  // error-tinted styling and a trailing delete icon, and stays visible instead of only on hover so
  // it isn't missed on a card the source can no longer resolve fresh data for.
  missing?: boolean;
}

// Its own component rather than markup inlined in AnimeCard: the continue-watching row's frame
// cards (ContinueWatchingRow) aren't AnimeCards at all - they show a captured video frame - but
// need the exact same badge, in the same corner, revealed by the same hover.
//
// Positioned absolutely against whatever it's dropped into, and keyed off that container's own
// `group` hover - so the parent has to be `relative` and carry `group`.
export function SourceBadge({ source }: { source?: AnimeCardSource }) {
  if (!source) return null;
  return (
    <div
      className={cn(
        "pointer-events-none absolute left-2 top-2 z-10 flex max-w-[calc(100%-16px)] items-center gap-1.5 rounded-full py-1 pl-1 pr-2.5 shadow-sm backdrop-blur-sm transition-opacity duration-200",
        source.missing ? "bg-rose-500/20 opacity-100" : "bg-black/70 opacity-0 group-hover:opacity-100",
      )}
    >
      <span className={cn("flex h-4 w-4 shrink-0 items-center justify-center overflow-hidden rounded-full", source.missing ? "bg-rose-500/20" : "bg-white/15")}>
        {source.iconUrl ? <img src={source.iconUrl} alt="" className="h-full w-full object-cover" /> : <Radio className={cn("h-2.5 w-2.5", source.missing ? "text-rose-200" : "text-zinc-300")} strokeWidth={2} />}
      </span>
      <span className={cn("truncate text-[11px] font-medium", source.missing ? "text-rose-200" : "text-zinc-100")}>{source.name}</span>
      {source.missing && <Trash2 className="h-2.5 w-2.5 shrink-0 text-rose-200" strokeWidth={2.5} />}
    </div>
  );
}

interface PosterCardProps {
  title: string;
  posterUrl?: string | null;
  type?: string | null;
  year?: number | null;
  episodeCount?: number | null;
  rating?: number | null;
  genres?: string[];
  description?: string | null;
  progress?: number;
  source?: AnimeCardSource;
  /** Drops episode count/rating from the meta line, keeping just the year - for a card given only
   * a narrow fixed-width column (profile's "continue watching" preview) rather than the usual
   * poster-grid width. The full line wrapped or truncated mid-word there once the type badge, year,
   * episode count and rating all had to share ~180px; the year alone always fits. */
  compact?: boolean;
}

/** Shared visual body for source titles and provider entries; navigation stays with each caller. */
export function PosterCard({ title, posterUrl, type, year, episodeCount, rating, genres, description, progress, source, compact }: PosterCardProps) {
  const { t } = useTranslation();
  const meta = compact ? [year].filter(Boolean).join(" · ") : [
    year,
    episodeCount && t("common.episodesShort", { count: episodeCount }),
    rating != null && `★ ${formatRating(rating)}`,
  ].filter(Boolean).join(" · ");
  return <>
    <div className="relative aspect-[2/3] overflow-hidden rounded-xl bg-surface ring-1 ring-border">
      {posterUrl ? <SmoothImage
        src={posterUrl}
        alt={title}
        loading="lazy"
        // `will-change` only while actually hovered, not unconditionally - applied to every card
        // at once, it makes the browser keep a persistent GPU compositing layer alive for every
        // single poster in the grid for as long as it's mounted, not just the one about to
        // transform. That layer bookkeeping cost scales with how many cards have accumulated as
        // more catalog pages load, working against scroll performance instead of for it - the
        // opposite of what `will-change` is supposed to buy here.
        className="h-full w-full transition-transform duration-500 ease-out group-hover:scale-[1.035] group-hover:will-change-transform"
      /> : <div className="flex h-full items-center justify-center px-3 text-center text-sm text-muted">{t("common.noPoster")}</div>}
      <SourceBadge source={source} />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/95 via-black/60 to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 translate-y-1.5 p-3 opacity-0 transition-[opacity,transform] duration-300 group-hover:translate-y-0 group-hover:opacity-100">
        {genres && genres.length > 0 && (
          <div className="mb-1.5 flex flex-wrap gap-1">
            {genres.slice(0, 3).map((genre) => (
              <span key={genre} className="rounded-md bg-white/20 px-1.5 py-0.5 text-[11px] font-medium text-white shadow-sm">{genre}</span>
            ))}
          </div>
        )}
        {description ? (
          <p className="line-clamp-4 select-text text-xs leading-relaxed text-zinc-300">{description}</p>
        ) : meta ? (
          // A source that doesn't hand back a description (or genres) left this reveal showing
          // nothing but a darkened poster and, at most, a lone genre chip - hovering promised more
          // than it delivered. The type/year/rating line already sits below every card regardless
          // of what the source provides, so it's always there to fall back on here too.
          <p className="line-clamp-2 select-text text-xs font-medium leading-relaxed text-zinc-300">{meta}</p>
        ) : !genres?.length ? (
          <p className="line-clamp-2 select-text text-base font-semibold leading-snug text-white">{title}</p>
        ) : null}
      </div>
      {progress !== undefined && <div className="absolute inset-x-0 bottom-0 h-[3px] bg-white/15 opacity-0 transition-opacity duration-300 group-hover:opacity-100"><div className="h-full bg-red-500" style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} /></div>}
    </div>
    <p className="mt-3 line-clamp-2 select-text text-base font-semibold leading-snug tracking-[-.01em] text-text/90 transition-colors group-hover:text-text mobile:mt-2 mobile:text-[13px] mobile:leading-[1.25]">{title}</p>
    <div className="mt-1 flex items-center gap-1.5 mobile:mt-0.5">
      {type && <span className="shrink-0 rounded-md bg-text/10 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-muted mobile:hidden">{type}</span>}
      <p className="line-clamp-1 text-sm text-muted mobile:text-[11.5px]">{meta || t("common.anime")}</p>
    </div>
  </>;
}

export const AnimeCard = memo(function AnimeCard({ anime, progress, source, compact }: { anime: AnimeTitle; progress?: number; source?: AnimeCardSource; compact?: boolean }) {
  const title = animeTitle(anime);
  return <Link
    to="/anime/$sourceId/$animeId"
    params={{ sourceId: anime.sourceId, animeId: anime.id }}
    // content-visibility stays off on the phone: there cards sit in sideways rows, and an off-screen
    // card's 440px placeholder height stretched the whole row to it.
    className="group block w-full [contain-intrinsic-size:auto_440px] [content-visibility:auto] mobile:[content-visibility:visible]"
  >
    <PosterCard
      title={title}
      posterUrl={anime.posterUrl}
      type={anime.type}
      year={anime.year}
      episodeCount={anime.availableEpisodeCount}
      rating={anime.ratings?.[0]?.value}
      genres={anime.genres}
      description={anime.description}
      progress={progress}
      source={source}
      compact={compact}
    />
  </Link>;
});
