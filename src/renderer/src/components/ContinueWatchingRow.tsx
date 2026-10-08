import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import * as ContextMenu from "@radix-ui/react-context-menu";
import { ChevronLeft, ChevronRight, Trash2 } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimeCard, SkeletonCard, type AnimeCardSource } from "@/components/AnimeCard";
import { HorizontalScrollRow } from "@/components/HorizontalScrollRow";
import { useContinueWatching, type ContinueWatchingSlot } from "@/lib/continueWatching";
import { WatchFrameCard } from "@/components/WatchFrameCard";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { useKnownSourcesStore } from "@/stores/knownSourcesStore";
import { hibiki } from "@/lib/hibiki";
import { isMobile } from "@/lib/mobile";
import type { WatchProgress } from "@shared/types";

// Same breakpoint the catalog/"you might like" Grid uses for its own 5→6 column jump (see
// index.tsx/catalog.tsx) - kept in sync so both read as the same underlying layout rule rather
// than two grids that happen to look similar. `.4` of an extra card's width is left over so the
// next one visibly peeks in at the edge (a scroll affordance, not just an arrow) instead of
// cutting off exactly on a card boundary.
const CARD_WIDTH_CLASSES = "w-[calc((100%-4*1rem)/5.4)] xl:w-[calc((100%-5*1rem)/6.4)]";

// Shared by the home page and the profile page - same underlying data (see useContinueWatching).
// Home shows the whole list (no `limit`) as a horizontally-scrolling row with arrows, since there
// can be more titles than fit on screen. Profile shows a bounded preview (`limit`, currently 4) -
// that's meant to always be fully visible with nothing to scroll to, so it renders as a fixed grid
// of exactly `limit` equal-width columns instead: fixed-width scrollable cards there would either
// overflow the narrower profile column (clipping the last one under the arrow, needing a scroll to
// see a title that's supposedly already all there is) or leave a false "you can scroll" affordance
// with nothing more behind it.
export function ContinueWatchingRow({ limit, sourceById }: { limit?: number; sourceById?: Map<string, AnimeCardSource> }) {
  const { slots } = useContinueWatching();
  const visible = limit ? slots.slice(0, limit) : slots;

  if (visible.length === 0) return null;

  if (limit) {
    return (
      <div className="grid gap-4" style={{ gridTemplateColumns: `repeat(${limit}, minmax(0, 1fr))` }}>
        {visible.map((slot) => (
          // Compact meta (see AnimeCard/PosterCard) - a fixed `limit`-column grid gives each card
          // only a slice of the profile's own (already narrower-than-a-full-page) column, nowhere
          // near enough room for the usual year/episodes/rating line without it truncating mid-word.
          <ContinueWatchingCard key={`${slot.progress.sourceId}:${slot.progress.titleId}`} slot={slot} sourceById={sourceById} compact />
        ))}
      </div>
    );
  }

  return (
    <HorizontalScrollRow
      items={visible}
      getKey={(slot) => `${slot.progress.sourceId}:${slot.progress.titleId}`}
      renderItem={(slot) => <ContinueWatchingCard slot={slot} sourceById={sourceById} />}
      cardWidthClassName={CARD_WIDTH_CLASSES}
    />
  );
}

// Exported for the full history page (routes/history.tsx), which renders every entry as a plain
// poster grid instead of this file's own bounded/scrollable layouts - it wants the exact same
// card (progress bar, delete-progress context menu, missing-source badge) without duplicating it.
export function ContinueWatchingCard({ slot, sourceById, compact }: { slot: ContinueWatchingSlot; sourceById?: Map<string, AnimeCardSource>; compact?: boolean }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const popoverTheme = usePopoverTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const knownSources = useKnownSourcesStore((s) => s.byId);

  if (slot.anime === undefined) return <SkeletonCard />;
  if (slot.anime === null) return null;

  const { sourceId, titleId } = slot.progress;
  // The installed source list wins when it's there; once it's uninstalled, fall back to whatever
  // name/icon was last remembered for it (see knownSourcesStore) so the badge still reads as that
  // source, just flagged as gone - rather than the card losing its source badge entirely.
  const installedSource = sourceById?.get(sourceId);
  const remembered = knownSources[sourceId];
  const cardSource: AnimeCardSource | undefined = installedSource ?? (remembered ? { ...remembered, missing: true } : undefined);
  const onRemoveProgress = async () => {
    // Gone from the row the instant it is chosen, not after the round trip and refetch - the list
    // used to sit there for a beat and then redraw.
    queryClient.setQueriesData<WatchProgress[]>({ queryKey: ["recent-progress"] }, (current) => current?.filter((row) => !(row.sourceId === sourceId && row.titleId === titleId)));
    await hibiki.progress.removeForAnime(sourceId, titleId);
    queryClient.invalidateQueries({ queryKey: ["recent-progress"] });
    queryClient.invalidateQueries({ queryKey: ["progress-all", sourceId, titleId] });
  };

  return (
    <ContextMenu.Root onOpenChange={setMenuOpen}>
      <ContextMenu.Trigger asChild>
        <div>
          <AnimeCard
            anime={slot.anime}
            progress={slot.progress.durationMs ? (slot.progress.positionMs / slot.progress.durationMs) * 100 : 0}
            source={cardSource}
            compact={compact}
          />
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
                  {t("catalog.removeProgress")}
                </ContextMenu.Item>
              </motion.div>
            </ContextMenu.Content>
          )}
        </AnimatePresence>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}

// Keep preview geometry identical whether a captured frame is available or not.
export function ContinueWatchingFrameRow({ sourceById }: { sourceById?: Map<string, AnimeCardSource> }) {
  const { slots } = useContinueWatching();
  const { t } = useTranslation();
  const scroller = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ left: false, right: false });
  const visible = slots.filter((slot) => slot.anime !== null);
  useEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const update = () => {
      if (!el.clientWidth) return;
      const left = el.scrollLeft > 4;
      const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 4;
      setEdges((prev) => prev.left === left && prev.right === right ? prev : { left, right });
    };
    const observer = new ResizeObserver(update);
    observer.observe(el);
    el.addEventListener("scroll", update, { passive: true });
    update();
    return () => { observer.disconnect(); el.removeEventListener("scroll", update); };
  }, [visible.length]);
  if (!visible.length) return null;
  const scroll = (direction: number) => {
    const el = scroller.current;
    if (!el) return;
    const card = el.firstElementChild as HTMLElement | null;
    el.scrollBy({ left: direction * ((card?.offsetWidth ?? el.clientWidth / 3) + 16) * 3,
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };
  if (isMobile) {
    // Phone: wide frames that run to the screen edges, the next one peeking in; the thumb scrolls,
    // so no arrows.
    return (
      <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto overscroll-x-contain px-4 pb-1">
        {visible.map((slot) => (
          <div key={`${slot.progress.sourceId}:${slot.progress.titleId}`} className="w-[64vw] max-w-[300px] min-w-0 shrink-0">
            <WatchFrameCard slot={slot} sourceById={sourceById} />
          </div>
        ))}
      </div>
    );
  }
  const buttonClass = "pointer-events-auto flex h-7 w-7 items-center justify-center rounded-full border border-white/10 bg-black/60 text-zinc-200 shadow-md backdrop-blur-sm transition-[transform,background-color,border-color,color] hover:scale-110 hover:border-white/20 hover:bg-black/80 hover:text-white active:scale-95";
  return (
    <div className="relative" style={{ "--frame-width": visible.length > 3 ? "calc((100% - 48px) / 3.2)" : "calc((100% - 32px) / 3)" } as React.CSSProperties}>
      <div ref={scroller} className="no-scrollbar flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2">
        {visible.map((slot) => (
          <div key={`${slot.progress.sourceId}:${slot.progress.titleId}`} className="min-w-0 shrink-0 snap-start" style={{ width: "var(--frame-width)" }}>
            <WatchFrameCard slot={slot} sourceById={sourceById} />
          </div>
        ))}
      </div>
      {/* Match the arrows to the preview's center, excluding the text below it. */}
      <div className="pointer-events-none absolute left-0 top-0 aspect-video" style={{ width: "var(--frame-width)" }}>
        {edges.left && <button type="button" aria-label={t("common.scrollLeft")} onClick={() => scroll(-1)} className="pointer-events-auto absolute inset-y-0 left-0 flex w-20 cursor-pointer items-center justify-start bg-gradient-to-r from-black/70 via-black/30 to-transparent pl-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-white">
          <span aria-hidden="true" className={buttonClass}><ChevronLeft className="h-4 w-4" /></span>
        </button>}
      </div>
      <div className="pointer-events-none absolute right-0 top-0 aspect-video" style={{ width: "var(--frame-width)" }}>
        {edges.right && <button type="button" aria-label={t("common.scrollRight")} onClick={() => scroll(1)} className="pointer-events-auto absolute inset-y-0 right-0 flex w-20 cursor-pointer items-center justify-end bg-gradient-to-l from-black/70 via-black/30 to-transparent pr-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-white">
          <span aria-hidden="true" className={buttonClass}><ChevronRight className="h-4 w-4" /></span>
        </button>}
      </div>
    </div>
  );
}
