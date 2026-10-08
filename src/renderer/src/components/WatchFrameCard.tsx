import { useState } from "react";
import { Link } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import * as ContextMenu from "@radix-ui/react-context-menu";
import { Check, Play, Trash2 } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { animeTitle, SourceBadge, type AnimeCardSource } from "@/components/AnimeCard";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { useKnownSourcesStore } from "@/stores/knownSourcesStore";
import type { ContinueWatchingSlot } from "@/lib/continueWatching";
import { hibiki } from "@/lib/hibiki";
import type { WatchProgress } from "@shared/types";

// The 16:9 card built around a captured frame from the player, shared by the home page's
// "continue watching" shelf and the full history page - the two used to carry their own
// near-identical copies of it, which is how they drifted apart (different hover treatment,
// different link target, a source badge on only one of them).
//
// The differences that are actually meaningful stay as props: history labels each card with when
// it was watched, flags finished episodes, and deletes a single episode's entry, while the home
// row is undated and clears a title's progress outright.
export interface WatchFrameCardProps {
  slot: ContinueWatchingSlot;
  sourceById?: Map<string, AnimeCardSource>;
  // e.g. "Today" / "3 Sep" - history only; the home row's cards are always the recent ones.
  dateLabel?: string;
  showWatchedBadge?: boolean;
  // Render entries whose title metadata couldn't be resolved (dead source, title pulled) instead
  // of dropping them. The home row skips those - there's nothing to continue watching from a
  // title that can't be loaded - but the history feed is a record of what you watched, and
  // silently losing rows from it would be worse than a card with only the id for a name.
  showUnresolved?: boolean;
  // Defaults to clearing the whole title's progress (what the home row wants).
  remove?: { label: string; run: () => Promise<void> };
}

export function WatchFrameCard({ slot, sourceById, dateLabel, showWatchedBadge, showUnresolved, remove }: WatchFrameCardProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const knownSources = useKnownSourcesStore((s) => s.byId);
  const popoverTheme = usePopoverTheme();
  const [menuOpen, setMenuOpen] = useState(false);

  if (slot.anime === undefined) {
    return <div><div className="aspect-video animate-pulse rounded-xl bg-text/[.06]" /><div className="mt-2.5 h-3.5 w-4/5 animate-pulse rounded bg-text/[.06]" /><div className="mt-1.5 h-3 w-2/5 animate-pulse rounded bg-text/[.05]" /></div>;
  }
  if (slot.anime === null && !showUnresolved) return null;

  const { progress } = slot;
  // Null only when `showUnresolved` let it through - every field read off it below is optional.
  const anime = slot.anime;
  const { sourceId, titleId, episodeId, groupId } = progress;
  const title = anime ? animeTitle(anime) : titleId;
  const isMovie = anime?.type?.toLowerCase() === "movie";
  const remainingMs = Number.isFinite(progress.durationMs) && progress.durationMs > 0
    ? Math.max(0, progress.durationMs - Math.max(0, progress.positionMs)) : null;
  const remainingLabel = remainingMs === null ? null : remainingMs === 0
    ? t("detail.playbackFinished")
    : remainingMs < 60000 ? t("detail.remainingUnderMinute")
    : t("detail.remainingMinutes", { minutes: Math.ceil(remainingMs / 60000) });
  const playbackLabel = [!isMovie && t("detail.episodeFallback", { number: progress.episodeNumber }), remainingLabel].filter(Boolean).join(" · ");
  const rating = anime?.ratings?.[0]?.value;

  const percent = progress.durationMs ? Math.min(100, Math.max(0, (progress.positionMs / progress.durationMs) * 100)) : 0;
  // Same installed-then-remembered fallback as the poster card above (see ContinueWatchingCard).
  const installedSource = sourceById?.get(sourceId);
  const remembered = knownSources[sourceId];
  const cardSource: AnimeCardSource | undefined = installedSource ?? (remembered ? { ...remembered, missing: true } : undefined);
  // Both call sites end up invalidating the same two queries; only *what* gets removed differs
  // (a title's whole progress on the home row, one episode's entry in the history feed).
  const onRemoveProgress = async () => {
    // Whole-title removal only: the history feed removes a single episode's row, and dropping every
    // row of the title from its cache would take the other episodes with it until the refetch.
    if (!remove) {
      // Gone from the row the instant it is chosen, not after the round trip and refetch - the list
      // used to sit there for a beat and then redraw.
      queryClient.setQueriesData<WatchProgress[]>({ queryKey: ["recent-progress"] }, (current) => current?.filter((row) => !(row.sourceId === sourceId && row.titleId === titleId)));
    }
    await (remove ? remove.run() : hibiki.progress.removeForAnime(sourceId, titleId));
    queryClient.invalidateQueries({ queryKey: ["recent-progress"] });
    queryClient.invalidateQueries({ queryKey: ["progress-all", sourceId, titleId] });
  };

  // A captured frame carries enough of its own identity (episode number, progress bar) that
  // jumping straight into the player - skipping the detail page entirely - reads as "pick up
  // exactly where you left off" rather than a shortcut that needs explaining. Only possible once
  // `groupId` is known (see watch_progress's own column comment) - progress saved before that
  // existed falls back to the detail page, same as the poster row above.
  const linkProps = groupId
    ? { to: "/watch/$sourceId/$animeId/$groupId/$episodeId" as const, params: { sourceId, animeId: titleId, groupId, episodeId } }
    : { to: "/anime/$sourceId/$animeId" as const, params: { sourceId, animeId: titleId } };

  return (
    <ContextMenu.Root onOpenChange={setMenuOpen}>
      <ContextMenu.Trigger asChild>
        {/* Two separate links, not one covering the whole card - the frame resumes playback
            (jumping straight into the player), which reads as a completely different action from
            "go look at this title's page", so clicking the name specifically shouldn't also yank
            you into the player. */}
        <div>
          {/* `as any` - `linkProps` picks between two differently-shaped routes at runtime (whether
              `groupId` is known), which TanStack Router's own `to`-driven prop inference can't
              narrow from a plain conditional like that; both branches are well-formed on their own. */}
          <Link {...(linkProps as any)} className="group block">
            <div className="relative aspect-video overflow-hidden rounded-xl bg-surface ring-1 ring-border transition-shadow duration-300 group-hover:shadow-[0_16px_40px_rgba(0,0,0,.45)]">
              {progress.thumbnailDataUrl ? (
                <img src={progress.thumbnailDataUrl} alt="" className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]" />
              ) : anime?.posterUrl ? (
                <>
                  <img src={anime.posterUrl} alt="" aria-hidden="true" className="absolute inset-0 h-full w-full scale-110 object-cover opacity-[.35] blur-xl" />
                  <div className="absolute inset-0 bg-black/40" />
                  <div className="absolute inset-0 flex items-center justify-center p-3">
                    <img src={anime.posterUrl} alt="" className="h-full max-w-full rounded-md object-contain shadow-xl" />
                  </div>
                </>
              ) : (
                <div className="absolute inset-0 flex items-center justify-center text-muted"><Play className="h-8 w-8" /></div>
              )}
              <SourceBadge source={cardSource} />
              {showWatchedBadge && progress.watched && (
                <span className="absolute right-2 top-2 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-emerald-500/90 text-white shadow">
                  <Check className="h-3 w-3" strokeWidth={3} />
                </span>
              )}
              <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/30 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white/90 text-zinc-900 shadow-lg">
                  <Play className="ml-0.5 h-5 w-5 fill-current" strokeWidth={0} />
                </span>
              </div>
              {/* Hover-only: the row is a wall of frames, and a red bar burned across the bottom of
                  every one made the shelf read as busier than it is. The remaining-time label under
                  the card still carries the same information at rest. */}
              {percent > 0 && (
                // Always shown on the phone: there is no hover to reveal it, and a row there holds a
                // frame or two, not a wall of them.
                <div className="pointer-events-none absolute inset-x-0 bottom-0 h-[3px] bg-white/15 opacity-0 transition-opacity duration-200 group-hover:opacity-100 mobile:opacity-100"><div className="h-full bg-red-500" style={{ width: `${percent}%` }} /></div>
              )}
            </div>
          </Link>
          <Link to="/anime/$sourceId/$animeId" params={{ sourceId, animeId: titleId }} className="group mt-2.5 block">
            <p className="line-clamp-1 text-sm font-semibold text-text/90 underline-offset-2 transition-colors group-hover:text-text group-hover:underline">{title}</p>
          </Link>
          <div className="mt-1 flex items-center justify-between gap-2">
            <p className="min-h-4 min-w-0 line-clamp-1 text-xs text-muted">{playbackLabel}</p>
            {dateLabel && <p className="shrink-0 text-xs text-muted">{dateLabel}</p>}
          </div>
          <div className="mt-1.5 flex min-h-4 items-center gap-1.5 text-xs text-muted mobile:hidden">
            {anime?.type && <span className="rounded-md bg-text/10 px-1.5 py-0.5 text-[10px] font-bold uppercase">{anime.type}</span>}
            {anime?.year ? <span>{anime.year}</span> : null}
            {rating != null && Number.isFinite(rating) && <span>{anime?.type || anime?.year ? "· " : ""}★ {Number.isInteger(rating) ? rating : rating.toFixed(2)}</span>}
          </div>
        </div>
      </ContextMenu.Trigger>
      <ContextMenu.Portal forceMount>
        <AnimatePresence>
          {menuOpen && (
            <ContextMenu.Content asChild forceMount>
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ type: "spring", stiffness: 500, damping: 45 }}
                style={popoverTheme}
                className="z-50 w-56 overflow-hidden rounded-xl border border-border bg-app-popover shadow-2xl"
              >
                <ContextMenu.Item
                  onSelect={onRemoveProgress}
                  className="flex cursor-default items-center gap-2.5 px-3.5 py-2.5 text-sm text-rose-300 outline-none transition-colors data-[highlighted]:bg-text/[.06]"
                >
                  <Trash2 className="h-4 w-4 shrink-0" strokeWidth={2} />
                  {remove ? remove.label : t("catalog.removeProgress")}
                </ContextMenu.Item>
              </motion.div>
            </ContextMenu.Content>
          )}
        </AnimatePresence>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}
