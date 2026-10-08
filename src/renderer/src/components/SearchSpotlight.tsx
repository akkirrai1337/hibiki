import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useRouter } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, ChevronUp, Clock, CornerDownLeft, LayoutGrid, Search, X } from "lucide-react";
import { cn } from "@/lib/cn";
import { hibiki, searchSource } from "@/lib/hibiki";
import { animeTitle } from "@/components/AnimeCard";
import { FilterSections, FiltersControl, pickedCount, useLiveFilters } from "@/components/CatalogFilters";
import { SmoothImage } from "@/components/SmoothImage";
import { usePopoverTheme } from "@/lib/usePopoverTheme";
import { activeFilterCount, toSearchRequestFilters } from "@/lib/searchFilters";
import { useUiStore } from "@/stores/uiStore";
import { useSpotlightStore } from "@/stores/spotlightStore";
import { useSearchHistoryStore } from "@/stores/searchHistoryStore";
import { useSearchFiltersStore } from "@/stores/searchFiltersStore";
import { isMobile, useBackHandler } from "@/lib/mobile";
import type { AnimeTitle } from "@shared/types";

const MIN_QUERY_LENGTH = 3;
const RESULT_LIMIT = 8;
const TYPE_DEBOUNCE_MS = 250;

/**
 * The app's search: a panel laid over whatever is on screen (Ctrl/Cmd+K, "/" outside a field, or the search
 * buttons) - a field with its filters, the matches under it, and the keyboard for everything: arrows to
 * move, Enter to open, Esc to leave. It is the only place to search, so it is also what comes back when
 * the user returns (Back) to the page they searched from after opening a result.
 *
 * Always mounted (it only draws while open) so that the two things that must outlive the panel live here:
 * the filters reset when the source changes, and the reopening on the way back.
 */
export function SearchSpotlight() {
  const open = useSpotlightStore((s) => s.open);
  const router = useRouter();
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const sourceId = (sources.data?.find((s) => s.id === activeSourceId) ?? sources.data?.[0])?.id;

  // A different source has its own filter ids and options - stale ones would mean nothing to it.
  const resetFilters = useSearchFiltersStore((s) => s.resetFilters);
  useEffect(() => resetFilters(), [sourceId, resetFilters]);

  // Back to the page a result was opened from: the search comes back as it was left.
  useEffect(
    () =>
      router.history.subscribe(({ location, action }) => {
        const state = useSpotlightStore.getState();
        if (!state.returnTo || action.type !== "BACK" || location.href !== state.returnTo.href) return;
        state.setInitialValue(state.returnTo.value);
        state.setRestoring(true);
        state.setReturnTo(null);
        state.setOpen(true);
      }),
    [router],
  );

  return createPortal(<AnimatePresence>{open && <SpotlightPanel key="spotlight" />}</AnimatePresence>, document.body);
}

function SpotlightPanel() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const router = useRouter();
  const popoverTheme = usePopoverTheme();
  const setOpen = useSpotlightStore((s) => s.setOpen);
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const recent = useSearchHistoryStore((s) => s.queries);
  const addRecent = useSearchHistoryStore((s) => s.add);
  const removeRecent = useSearchHistoryStore((s) => s.remove);
  const clearRecent = useSearchHistoryStore((s) => s.clear);
  const filters = useSearchFiltersStore((s) => s.filters);
  const setFilters = useSearchFiltersStore((s) => s.setFilters);
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const source = sources.data?.find((s) => s.id === activeSourceId) ?? sources.data?.[0];
  const filterCatalog = useQuery({
    queryKey: ["filterCatalog", source?.id],
    enabled: !!source,
    queryFn: () => hibiki.sources.filterCatalog(source!.id),
  });

  const [value, setValue] = useState(() => useSpotlightStore.getState().initialValue);
  const [settled, setSettled] = useState(() => useSpotlightStore.getState().initialValue.trim());
  // -1 is "no row": the pointer left the list, so the highlight it put there goes with it. A highlight the
  // keyboard put there stays.
  const [selected, setSelected] = useState(0);
  const viaPointer = useRef(false);
  const pointAt = (index: number) => { viaPointer.current = true; setDefaultSelection(false); setSelected(index); };
  // Nothing picked yet: Enter should mean "see everything", not "open whatever happens to be
  // first" - so until an arrow key or the pointer actually lands on a row, the highlighted (and
  // Enter-activated) row is "all results" itself rather than index 0. See effectiveSelected below.
  const [defaultSelection, setDefaultSelection] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const { draft, change } = useLiveFilters(filters, setFilters);
  const filterDefs = filterCatalog.data?.filters ?? [];
  const count = pickedCount(filterDefs, filters);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Opened fresh, the panel starts clean; opened on the way back, it keeps the filters it was left with.
  useEffect(() => {
    const state = useSpotlightStore.getState();
    if (!state.restoring) useSearchFiltersStore.getState().resetFilters();
    state.setRestoring(false);
    state.setInitialValue("");
    inputRef.current?.focus();
  }, []);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value.trim()), TYPE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value]);

  const hasFilters = activeFilterCount(filters) > 0;
  const longEnough = settled.length >= MIN_QUERY_LENGTH;
  // A picked filter is a request on its own; a one- or two-letter fragment is left out of it (some sources
  // reject or answer noisily to it).
  const searching = longEnough || hasFilters;
  // Only the first few: the panel is for getting to a title fast, and the rest is one step on (openAll).
  const results = useQuery({
    queryKey: ["spotlightFirst", source?.id, longEnough ? settled : "", filters],
    enabled: !!source && searching,
    queryFn: ({ signal }) => searchSource(source!.id, { query: longEnough ? settled : undefined, limit: RESULT_LIMIT, ...toSearchRequestFilters(filters) }, signal),
  });
  const items: AnimeTitle[] = useMemo(() => (searching ? (results.data ?? []) : []), [searching, results.data]);
  // With nothing typed and no filter, the list is the recent queries; picking one fills the field.
  const showingRecent = value.trim().length === 0 && !hasFilters && recent.length > 0;
  // The last row is "all results", reachable with the arrows like any other.
  const hasAll = !showingRecent && items.length > 0;
  const rowCount = showingRecent ? recent.length : items.length + (hasAll ? 1 : 0);
  // The row Enter/highlighting actually act on: the real `selected` once the user has touched the
  // list, or "all results" (falling back to the first item when there's nothing to view "all" of)
  // while it's still untouched - see `defaultSelection` above.
  const effectiveSelected = !showingRecent && defaultSelection ? (hasAll ? items.length : items.length > 0 ? 0 : -1) : selected;
  useEffect(() => { viaPointer.current = false; setDefaultSelection(true); setSelected(0); }, [settled, showingRecent, filters]);
  // Only a row the keyboard moved to is scrolled into view; one the pointer is over is already under it, and
  // scrolling it would make the list jump away from the pointer.
  useEffect(() => {
    if (viaPointer.current) return;
    listRef.current?.querySelector<HTMLElement>(`[data-row="${selected}"]`)?.scrollIntoView({ block: "nearest" });
  }, [selected]);

  const close = () => setOpen(false);
  // Back peels one layer off, as Esc does: the filters first, then the panel.
  useBackHandler(true, () => (filtersOpen ? setFiltersOpen(false) : close()));
  const openTitle = (anime: AnimeTitle) => {
    if (longEnough) addRecent(settled);
    // Where to come back to, and what was typed - see SearchSpotlight.
    useSpotlightStore.getState().setReturnTo({ href: router.history.location.href, value });
    close();
    void navigate({ to: "/anime/$sourceId/$animeId", params: { sourceId: anime.sourceId, animeId: anime.id } });
  };

  // What is in the field right now, not the debounced `settled` copy the results are built from: Enter
  // typed right after the last keystroke lands before the debounce has caught up, and opening "all
  // results" with the stale copy sent an empty query to the search page - which then showed only the
  // filter results (or nothing), exactly what hovering the list for a moment before Enter happened to
  // avoid.
  const openAll = () => {
    const query = value.trim();
    const queryLongEnough = query.length >= MIN_QUERY_LENGTH;
    if (queryLongEnough) addRecent(query);
    useSpotlightStore.getState().setReturnTo({ href: router.history.location.href, value });
    close();
    void navigate({ to: "/search", search: { q: queryLongEnough ? query : "" } });
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    // Esc peels one layer off: the filters first, then the panel.
    if (event.key === "Escape") { event.preventDefault(); if (filtersOpen) setFiltersOpen(false); else close(); return; }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      viaPointer.current = false;
      // Stepping off the default "all results" highlight starts from item 0, same as if it had
      // been sitting at the last real row already (rowCount - 1) and this were just the next step.
      setDefaultSelection(false);
      if (rowCount > 0) setSelected((i) => (defaultSelection ? 0 : (i + 1) % rowCount));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      viaPointer.current = false;
      setDefaultSelection(false);
      if (rowCount > 0) setSelected((i) => (defaultSelection || i < 0 ? rowCount - 1 : (i - 1 + rowCount) % rowCount));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      const row = effectiveSelected < 0 ? 0 : effectiveSelected;
      if (showingRecent) return setValue(recent[row] ?? "");
      // Nothing picked and the results on screen are still for an older version of the text: they are not
      // what was asked for, so "see everything" for what is actually typed.
      if (defaultSelection && value.trim() !== settled) return openAll();
      if (event.ctrlKey || event.metaKey) return hasAll ? openAll() : undefined;
      if (row === items.length) return openAll();
      const anime = items[row];
      if (anime) openTitle(anime);
    }
  };

  const hasList = showingRecent || searching || value.trim().length > 0;

  return (
    // The container itself does not fade: a backdrop blur inside an element whose opacity is animating is
    // composited as its own group and only lines up once the opacity settles, which read as a crooked
    // blur that snapped into place at the end. The scrim fades on its own; the panel has its own motion.
    // Phone: a screen of its own (opaque, under the status bar, above the keyboard) rather than a
    // panel floating over the page - there is no room around it to float in.
    <div className="fixed inset-0 z-[70] flex items-start justify-center px-6 pt-[14vh] mobile:px-3 mobile:pb-3 mobile:pt-[calc(0.625rem+var(--safe-top))]" onKeyDown={onKeyDown}>
      <motion.div className="absolute inset-0 bg-black/45 backdrop-blur-[3px] mobile:bg-[rgb(var(--color-bg))] mobile:backdrop-blur-none" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }} onClick={isMobile ? undefined : close} />
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -10 }}
        transition={{ type: "spring", stiffness: 520, damping: 38 }}
        className="relative w-full max-w-[680px] mobile:flex mobile:h-full mobile:max-w-none mobile:flex-col"
      >
        <div className="relative">
          <Search className="pointer-events-none absolute left-5 top-1/2 h-5 w-5 -translate-y-1/2 text-muted mobile:left-4" strokeWidth={2} />
          <input
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={t("catalog.searchPlaceholder")}
            aria-label={t("catalog.searchPlaceholder")}
            enterKeyHint="search"
            className={cn(
              "h-14 w-full rounded-2xl bg-app-popover pl-14 text-base text-text shadow-2xl outline-none placeholder:text-muted mobile:h-12 mobile:border mobile:border-border mobile:pl-12 mobile:text-[15px] mobile:shadow-none",
              filterDefs.length ? "pr-56 mobile:pr-[9.5rem]" : "pr-24 mobile:pr-12",
            )}
            style={popoverTheme}
          />
          <div className="absolute right-2.5 top-1/2 flex -translate-y-1/2 items-center gap-1.5">
            {value && (
              <button onClick={() => { setValue(""); inputRef.current?.focus(); }} aria-label={t("search.clear")} className="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.08] hover:text-text">
                <X className="h-4 w-4" strokeWidth={2.25} />
              </button>
            )}
            {filterDefs.length > 0 ? (
              <FiltersControl count={count} open={filtersOpen} onToggle={() => setFiltersOpen((v) => !v)} onClear={() => setFilters({})} />
            ) : (
              !isMobile && <kbd className="rounded-md border border-border px-1.5 py-0.5 text-[11px] font-semibold text-muted">Esc</kbd>
            )}
          </div>
        </div>

        {/* The filters open as a card under the field, in the same glass as the results, and push the
            results down instead of covering them - so what a filter does is visible while it is picked. */}
        <AnimatePresence initial={false}>
          {filtersOpen && filterDefs.length > 0 && (
            <motion.div
              key="filters"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2, ease: "easeOut" }}
              className="overflow-hidden"
            >
              <div style={popoverTheme} className="mt-2 rounded-2xl border border-border bg-app-popover shadow-2xl">
                <div className="flex items-center justify-between px-4 pb-1 pt-3">
                  <p className="text-[11px] font-bold uppercase tracking-wide text-muted">{t("search.filters.button")}</p>
                  <div className="flex items-center gap-1">
                    <button onClick={() => setFilters({})} disabled={count === 0} className="rounded-lg px-2 py-1 text-xs font-semibold text-muted transition-colors hover:text-text disabled:opacity-40">{t("search.filters.reset")}</button>
                    <button onClick={() => setFiltersOpen(false)} aria-label={t("search.filters.done")} className="flex h-7 w-7 items-center justify-center rounded-lg text-muted transition-colors hover:bg-text/[.08] hover:text-text">
                      <ChevronUp className="h-4 w-4" strokeWidth={2.25} />
                    </button>
                  </div>
                </div>
                <div className="no-scrollbar max-h-[38vh] overflow-y-auto px-4 pb-4 pt-2 mobile:max-h-[46vh]">
                  {filterCatalog.isLoading ? <p className="py-4 text-center text-sm text-muted">…</p> : <FilterSections defs={filterDefs} draft={draft} onChange={change} />}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {hasList && (
          <div
            ref={listRef}
            style={popoverTheme}
            onMouseLeave={() => {
              // The pointer leaving doesn't just clear the highlight visually - the row it had
              // put there stops being what Enter would act on too, same as if the list had never
              // been touched (back to "all results", not a stale index 0).
              if (!viaPointer.current) return;
              viaPointer.current = false;
              setDefaultSelection(true);
              setSelected(0);
            }}
            className={cn(
              "no-scrollbar mt-2 overflow-y-auto rounded-2xl border border-border bg-app-popover p-1.5 shadow-2xl",
              // Phone: the list takes the rest of the screen above the keyboard, flat on the page.
              "mobile:max-h-none mobile:min-h-0 mobile:flex-1 mobile:border-0 mobile:bg-transparent mobile:p-0 mobile:shadow-none",
              filtersOpen ? "max-h-[28vh]" : "max-h-[56vh]",
            )}
          >
            {showingRecent && (
              <>
                <div className="flex items-center justify-between px-3 pb-1 pt-2">
                  <p className="text-[11px] font-bold uppercase tracking-wide text-muted">{t("search.recent")}</p>
                  <button onClick={clearRecent} className="text-[11px] font-semibold text-muted transition-colors hover:text-text">{t("search.clearRecent")}</button>
                </div>
                {recent.map((query, index) => (
                  <button
                    key={query}
                    data-row={index}
                    onMouseMove={() => pointAt(index)}
                    onClick={() => { setValue(query); inputRef.current?.focus(); }}
                    onContextMenu={(e) => { e.preventDefault(); removeRecent(query); }}
                    title={t("search.removeRecent")}
                    className={cn("flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-text transition-colors", selected === index && "bg-text/[.08]")}
                  >
                    <Clock className="h-4 w-4 shrink-0 text-muted" strokeWidth={2} />
                    <span className="min-w-0 flex-1 truncate">{query}</span>
                  </button>
                ))}
              </>
            )}
            {!showingRecent && !searching && <p className="px-3 py-6 text-center text-sm text-muted">{t("search.minChars", { count: MIN_QUERY_LENGTH })}</p>}
            {searching && results.isLoading && <p className="px-3 py-6 text-center text-sm text-muted">…</p>}
            {searching && results.isError && <p className="px-3 py-6 text-center text-sm text-muted">{t("search.errorGeneric", { source: source?.name ?? t("search.source") })}</p>}
            {searching && results.isSuccess && items.length === 0 && <p className="px-3 py-6 text-center text-sm text-muted">{longEnough ? t("search.empty", { query: settled }) : t("search.filteredEmpty")}</p>}
            {items.map((anime, index) => {
              const meta = [anime.type ? anime.type.toUpperCase() : null, anime.year || null].filter(Boolean).join(" · ");
              return (
                <button key={`${anime.sourceId}:${anime.id}`} data-row={index} onMouseMove={() => pointAt(index)} onClick={() => openTitle(anime)} className={cn("flex w-full items-center gap-3.5 rounded-xl px-2.5 py-2 text-left transition-colors", effectiveSelected === index && "bg-text/[.08]")}>
                  <div className="h-[54px] w-9 shrink-0 overflow-hidden rounded-md bg-surface ring-1 ring-border">
                    {anime.posterUrl && <SmoothImage src={anime.posterUrl} alt="" className="h-full w-full object-cover" />}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-sm font-semibold text-text">{animeTitle(anime)}</p>
                    {meta && <p className="mt-0.5 line-clamp-1 text-xs text-muted">{meta}</p>}
                  </div>
                  {effectiveSelected === index && <CornerDownLeft className="h-4 w-4 shrink-0 text-muted" strokeWidth={2} />}
                </button>
              );
            })}
            {hasAll && (
              <button data-row={items.length} onMouseMove={() => pointAt(items.length)} onClick={openAll} className={cn("mt-1 flex w-full items-center gap-3.5 rounded-xl px-2.5 py-2 text-left transition-colors", effectiveSelected === items.length && "bg-text/[.08]")}>
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/15 text-accent-text">
                  <LayoutGrid className="h-[18px] w-[18px]" strokeWidth={2} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-text">{t("search.allResults")}</p>
                  <p className="mt-0.5 line-clamp-1 text-xs text-muted">{longEnough ? `«${settled}»` : t("search.filteredResults")}</p>
                </div>
                <ArrowRight className="h-4 w-4 shrink-0 text-muted" strokeWidth={2.25} />
              </button>
            )}
          </div>
        )}
      </motion.div>
    </div>
  );
}
