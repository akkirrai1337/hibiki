import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { PullToRefresh, refetchFromFirstPage } from "@/components/PullToRefresh";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useTranslation } from "react-i18next";
import { Radio } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { AnimeCard, PosterGrid, PosterGridSkeleton } from "@/components/AnimeCard";
import { ErrorBanner } from "@/components/ErrorBanner";
import { useUiStore } from "@/stores/uiStore";
import { CatalogModeMenu } from "@/components/CatalogModeMenu";
import { sortLabel } from "@/lib/catalogSort";
import { CatalogFilters } from "@/components/CatalogFilters";
import { useCatalogIntentStore } from "@/stores/catalogIntentStore";
import { activeFilterCount, toSearchRequestFilters, type SearchFilters } from "@/lib/searchFilters";
import { isMobile } from "@/lib/mobile";
import type { AnimeTitle } from "@shared/types";

// What "browse" can be sorted by is the source's business: it declares its own orders in
// getSettings().sortOptions and gets the chosen id back as `sort` - the menu is exactly that list.
function parseCatalogSearch(search: Record<string, unknown>): { sort: string | undefined } {
  return { sort: typeof search.sort === "string" ? search.sort : undefined };
}

// Three rows at the widest six-column layout. Smaller batches spread image decoding and DOM work
// over time instead of producing a visible frame spike whenever 30 posters arrive together.
const PAGE_SIZE = 18;

// A plain `Route.useSearch()` throws once this stays mounted while some other route is active (it
// requires an active match for this exact route) - reading straight off the location instead keeps
// working no matter which route is actually current.
export function CatalogBrowsePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const search = useRouterState({ select: (s) => s.location.search as Record<string, unknown> });
  const { sort: requestedMode } = parseCatalogSearch(search);
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const source = sources.data?.find((s) => s.id === activeSourceId) ?? sources.data?.[0];
  const setRequestedMode = (next: string) => navigate({ to: "/catalog", search: { sort: next }, replace: true });

  // Same key as the filter panel's query, so the source is asked once for both.
  const settings = useQuery({
    queryKey: ["filterCatalog", source?.id],
    enabled: !!source,
    queryFn: () => hibiki.sources.filterCatalog(source!.id),
  });
  const modes = useMemo(
    () => (settings.data?.sortOptions ?? []).map((option) => ({ value: option.id, label: sortLabel(option, t) })),
    [settings.data, t],
  );
  // The first order is the source's own default when nothing (or something no longer offered) was asked for.
  // The catalog's own filters, apart from the search box's: picking one here must not narrow a search.
  const [filters, setFilters] = useState<SearchFilters>({});
  // A different source has its own filter ids and options.
  useEffect(() => setFilters({}), [source?.id]);
  // "More of this genre" from a title page: taken once the catalog is showing that source, after the
  // reset above so it is not wiped by it.
  const intent = useCatalogIntentStore((s) => s.intent);
  const clearIntent = useCatalogIntentStore((s) => s.clear);
  useEffect(() => {
    if (!intent || source?.id !== intent.sourceId) return;
    setFilters(intent.filters);
    clearIntent();
  }, [intent, source?.id, clearIntent]);
  const filterDefs = settings.data?.filters ?? [];
  const filterCount = activeFilterCount(filters);

  const mode = modes.find((m) => m.value === requestedMode)?.value ?? modes[0]?.value;
  const sort = mode;

  const queryClient = useQueryClient();
  const browseKey = ["catalog", source?.id, sort ?? "", filters];
  const browse = useInfiniteQuery({
    queryKey: browseKey,
    // Wait for the source's orders: asking before them would browse by an order nobody chose.
    enabled: !!source && settings.isFetched,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      hibiki.sources.search(source!.id, { offset: pageParam, limit: PAGE_SIZE, sort, ...toSearchRequestFilters(filters) }),
    getNextPageParam: (lastPage, allPages) => (
      lastPage.length < PAGE_SIZE ? undefined : allPages.reduce((offset, page) => offset + page.length, 0)
    ),
  });
  const items = useMemo(() => browse.data?.pages.flat() ?? [], [browse.data]);
  const isLoading = settings.isLoading || browse.isLoading;
  const isError = browse.isError;
  const error = browse.error;

  const catalogAutoLoad = useUiStore((s) => s.catalogAutoLoad);
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = browse;
  const loadMoreRef = useRef<HTMLDivElement>(null);
  // Fires fetchNextPage itself once the sentinel below the grid scrolls into view, instead of
  // waiting for a click on the manual button - see the Settings toggle this is gated behind.
  // rootMargin gives it a head start (starts loading a bit before the sentinel is actually on
  // screen) so the next page is usually already there by the time scrolling reaches the bottom.
  useEffect(() => {
    if (!catalogAutoLoad) return;
    const el = loadMoreRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) fetchNextPage(); },
      { rootMargin: "300px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [catalogAutoLoad, hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="min-h-full bg-app-bg px-8 py-8 pb-16 mobile:px-4 mobile:pb-6 mobile:pt-3">
      <PullToRefresh onRefresh={() => (source ? refetchFromFirstPage(queryClient, browseKey) : sources.refetch())} />
      {sources.isLoading && <PosterGridSkeleton count={15} />}
      {sources.isError && <ErrorBanner message={(sources.error as Error).message} />}
      {sources.data?.length === 0 && <EmptySources />}
      {source && (
        <>
          <CatalogFilters
            defs={filterDefs}
            filters={filters}
            onChange={setFilters}
            loading={settings.isLoading}
          >
            {modes.length > 1 && <SortMenu mode={mode} modes={modes} onChange={setRequestedMode} />}
          </CatalogFilters>

          {isLoading ? (
            <PosterGridSkeleton count={15} />
          ) : items.length === 0 ? (
            <EmptyState text={t(filterCount > 0 ? "catalogPage.emptyFiltered" : "catalogPage.empty")} />
          ) : (
            <>
              <VirtualGrid items={items} />
              {hasNextPage && (
                <div ref={loadMoreRef} className="mt-8 flex justify-center">
                  {catalogAutoLoad ? (
                    isFetchingNextPage && <div className="h-7 w-7 animate-spin rounded-full border-2 border-border border-t-accent" />
                  ) : (
                    <button
                      onClick={() => fetchNextPage()}
                      disabled={isFetchingNextPage}
                      className="rounded-xl border border-border px-5 py-2.5 text-sm font-semibold text-text/80 transition-colors hover:bg-text/[.06] disabled:opacity-50"
                    >
                      {t("catalogPage.loadMore")}
                    </button>
                  )}
                </div>
              )}
            </>
          )}
          {isError && <ErrorBanner message={(error as Error).message} error={error} className="mt-6" />}
        </>
      )}
    </div>
  );
}

function SortMenu({ mode, modes, onChange }: { mode: string | undefined; modes: Array<{ value: string; label: string }>; onChange: (mode: string) => void }) {
  return <CatalogModeMenu value={mode ?? ""} options={modes} onChange={onChange} />;
}

const GRID_COLUMNS_DEFAULT = 5;
const GRID_COLUMNS_XL = 6;
const GRID_XL_QUERY = "(min-width: 1280px)";
const GRID_GAP_Y = isMobile ? 20 : 24; // px, matches PosterGrid's own `gap-y-6` (`mobile:gap-y-5`)

/** Same column-count rule as PosterGrid (grid-cols-5, xl:grid-cols-6) - kept in sync
 * manually since VirtualGrid needs the count as a number (to group items into rows) rather than
 * just a CSS class. */
function useGridColumnCount(): number {
  // A phone always has three, matching PosterGrid's `mobile:grid-cols-3`.
  const [columns, setColumns] = useState(() => (typeof window !== "undefined" && window.matchMedia(GRID_XL_QUERY).matches ? GRID_COLUMNS_XL : GRID_COLUMNS_DEFAULT));
  useEffect(() => {
    if (isMobile) {
      setColumns(3);
      return;
    }
    const mql = window.matchMedia(GRID_XL_QUERY);
    const onChange = () => setColumns(mql.matches ? GRID_COLUMNS_XL : GRID_COLUMNS_DEFAULT);
    onChange();
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, []);
  return columns;
}

// Row-virtualized version of PosterGrid, for the actual (potentially hundreds-of-cards-deep, once
// enough pages have loaded) catalog list - `content-visibility: auto` on each AnimeCard (see that
// component) already skips paint/layout for off-screen cards, but every one of them still exists
// as a real, permanently-mounted DOM subtree the whole time. Scrolling fast enough crosses many
// cards' visibility threshold within the same frame or two, forcing content-visibility's "catch
// up" layout+paint cost for all of them practically at once - a stutter content-visibility alone
// can reduce but not eliminate, since the DOM nodes (and the browser's per-element bookkeeping for
// each) are all still there regardless of whether any given one is currently painted. Rendering
// only the rows actually near the viewport (plus a small overscan) keeps the real DOM node count
// bounded no matter how many pages have been paged through.
function VirtualGrid({ items }: { items: AnimeTitle[] }) {
  const columns = useGridColumnCount();
  const rows = useMemo(() => {
    const out: AnimeTitle[][] = [];
    for (let i = 0; i < items.length; i += columns) out.push(items.slice(i, i + columns));
    return out;
  }, [items, columns]);

  // The virtualizer needs the actual scrolling element, but this page doesn't own one itself -
  // it's rendered straight inside __root.tsx's own per-page `overflow-y-auto` wrapper (see that
  // file), which is also what remembers scroll position across a page revisit by simply never
  // unmounting. Reaching up to `parentElement` piggybacks on that existing scroll container
  // instead of introducing a second, nested one (which would need its own scroll-position story).
  const [scrollElement, setScrollElement] = useState<HTMLElement | null>(null);
  const containerRef = useCallback((node: HTMLDivElement | null) => setScrollElement(node?.parentElement ?? null), []);

  const rowVirtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollElement,
    // A rough guess (poster + two-line title + meta row, at a typical card width) - corrected per
    // row against its real rendered height via `measureElement` below, so this only matters for
    // the very first estimate before anything's actually been measured.
    estimateSize: () => (isMobile ? 240 : 380),
    overscan: 4,
    gap: GRID_GAP_Y,
  });

  // `containerRef` has to be attached in both branches below (it's what resolves `scrollElement`
  // in the first place, via the callback ref above) - a version that only attached it once
  // `scrollElement` was already known would never get the chance to become known at all.
  return (
    <div ref={containerRef} style={scrollElement ? { position: "relative", height: rowVirtualizer.getTotalSize(), width: "100%" } : undefined}>
      {!scrollElement ? (
        // Before the ref callback above has resolved the scroll container (only ever the very
        // first render) - the real grid, unvirtualized, so there's an actual mounted node for that
        // callback to fire against; the resulting state update switches to the virtualized branch
        // immediately after, and this one never shows again.
        <PosterGrid>{items.map((item) => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</PosterGrid>
      ) : (
        rowVirtualizer.getVirtualItems().map((virtualRow) => (
          <div
            key={virtualRow.key}
            ref={rowVirtualizer.measureElement}
            data-index={virtualRow.index}
            style={{ position: "absolute", top: 0, left: 0, width: "100%", transform: `translateY(${virtualRow.start}px)` }}
          >
            <div className="grid gap-x-4 mobile:gap-x-3" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
              {rows[virtualRow.index].map((item) => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
function EmptyState({ text }: { text: string }) { return <div className="py-16 text-center text-sm text-muted">{text}</div>; }
function EmptySources() { const { t } = useTranslation(); return <div className="flex min-h-[calc(100vh-76px)] items-center justify-center"><div className="max-w-sm text-center"><div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-text/[.06]"><Radio className="h-6 w-6 text-muted" strokeWidth={1.75} /></div><h1 className="text-xl font-bold text-text">{t("catalog.emptySourcesTitle")}</h1><p className="mt-3 text-sm leading-6 text-muted">{t("catalog.emptySourcesText")}</p><Link to="/sources" className="mt-6 inline-block rounded-xl bg-accent px-4 py-2.5 text-sm font-bold text-accent-fg">{t("catalog.openSources")}</Link></div></div>; }
