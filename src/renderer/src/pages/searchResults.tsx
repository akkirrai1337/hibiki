import { useEffect, useRef, useState } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { PullToRefresh, refetchFromFirstPage } from "@/components/PullToRefresh";
import { useNavigate, useRouterState } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { Clock, Search, SlidersHorizontal, X } from "lucide-react";
import { hibiki, searchSource } from "@/lib/hibiki";
import { AnimeCard, PosterGrid, PosterGridSkeleton } from "@/components/AnimeCard";
import { ErrorBanner } from "@/components/ErrorBanner";
import { BottomSheet } from "@/components/BottomSheet";
import { FilterSections, pickedCount, useLiveFilters } from "@/components/CatalogFilters";
import { useUiStore } from "@/stores/uiStore";
import { useSearchFiltersStore } from "@/stores/searchFiltersStore";
import { useSearchHistoryStore } from "@/stores/searchHistoryStore";
import { activeFilterCount, toSearchRequestFilters } from "@/lib/searchFilters";
import { isMobile, mobileSearchMemory } from "@/lib/mobile";

const PAGE_SIZE = 30;
const MIN_QUERY_LENGTH = 3;
const TYPE_DEBOUNCE_MS = 350;

/** Every result of a search - the panel shows the first few, this shows them all, paged in as it is scrolled. */
export function SearchResultsPage() {
  return isMobile ? <MobileSearchPage /> : <DesktopSearchResults />;
}

/** A search's results for the current source and filters, paged in once the returned sentinel scrolls near. */
function useSearchResults(query: string) {
  const longEnough = query.length >= MIN_QUERY_LENGTH;
  const sources = useQuery({ queryKey: ["sources"], queryFn: () => hibiki.sources.list() });
  const activeSourceId = useUiStore((s) => s.activeSourceId);
  const source = sources.data?.find((s) => s.id === activeSourceId) ?? sources.data?.[0];
  const filters = useSearchFiltersStore((s) => s.filters);
  const hasFilters = activeFilterCount(filters) > 0;

  const queryClient = useQueryClient();
  const resultsKey = ["searchResults", source?.id, longEnough ? query : "", filters];
  const results = useInfiniteQuery({
    queryKey: resultsKey,
    enabled: !!source && (longEnough || hasFilters),
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) =>
      searchSource(source!.id, { query: longEnough ? query : undefined, offset: pageParam, limit: PAGE_SIZE, ...toSearchRequestFilters(filters) }, signal),
    getNextPageParam: (lastPage, allPages) => (lastPage.length < PAGE_SIZE ? undefined : allPages.reduce((offset, page) => offset + page.length, 0)),
  });
  const items = results.data?.pages.flat() ?? [];

  const { fetchNextPage, hasNextPage, isFetchingNextPage } = results;
  const sentinel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(([entry]) => { if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage(); }, { rootMargin: "400px" });
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, items.length]);

  const refresh = () => refetchFromFirstPage(queryClient, resultsKey);

  return { source, results, items, longEnough, hasFilters, sentinel, refresh };
}

function DesktopSearchResults() {
  const { t } = useTranslation();
  const search = useRouterState({ select: (s) => s.location.search as Record<string, unknown> });
  const query = (typeof search.q === "string" ? search.q : "").trim();
  const { source, results, items, longEnough, sentinel } = useSearchResults(query);

  return (
    <div className="min-h-full bg-app-bg px-8 py-8 pb-16">
      <h1 className="mb-5 select-text text-lg font-bold text-text">{longEnough ? t("search.resultsFor", { query }) : t("search.filteredResults")}</h1>
      {results.isLoading && <PosterGridSkeleton count={12} />}
      {results.isError && <ErrorBanner message={t("search.errorGeneric", { source: source?.name ?? t("search.source") })} error={results.error} />}
      {results.isSuccess && items.length === 0 && <div className="py-16 text-center text-sm text-muted">{longEnough ? t("search.empty", { query }) : t("search.filteredEmpty")}</div>}
      {items.length > 0 && <PosterGrid>{items.map((item) => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</PosterGrid>}
      {results.hasNextPage && (
        <div ref={sentinel} className="mt-8 flex justify-center">
          <div className="h-7 w-7 animate-spin rounded-full border-2 border-border border-t-accent" />
        </div>
      )}
    </div>
  );
}

/**
 * The phone's search tab: a page, not a panel - the field stays at the top while the results fill
 * the screen under it as a poster grid, the tab bar stays, and the filters rise as a sheet over it.
 * What is typed lives in the URL (?q=), so Back and the tab coming back keep it.
 */
function MobileSearchPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const search = useRouterState({ select: (s) => s.location.search as Record<string, unknown> });
  const urlQuery = typeof search.q === "string" ? search.q : "";
  const [value, setValue] = useState(urlQuery);
  const query = urlQuery.trim();
  const { source, results, items, longEnough, hasFilters, sentinel, refresh } = useSearchResults(query);

  // Typing settles into the URL after a pause; replace, so each keystroke is not a step for Back.
  // A URL changed from elsewhere (Back, a link) is taken into the field instead of overwritten.
  const pushed = useRef(urlQuery.trim());
  useEffect(() => {
    if (urlQuery.trim() === pushed.current) return;
    pushed.current = urlQuery.trim();
    setValue(urlQuery);
  }, [urlQuery]);
  useEffect(() => {
    mobileSearchMemory.query = value.trim();
    if (value.trim() === pushed.current) return;
    const timer = setTimeout(() => {
      pushed.current = value.trim();
      void navigate({ to: "/search", search: { q: value.trim() }, replace: true });
    }, TYPE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value, navigate]);

  const recent = useSearchHistoryStore((s) => s.queries);
  const addRecent = useSearchHistoryStore((s) => s.add);
  const removeRecent = useSearchHistoryStore((s) => s.remove);
  const clearRecent = useSearchHistoryStore((s) => s.clear);

  const filters = useSearchFiltersStore((s) => s.filters);
  const setFilters = useSearchFiltersStore((s) => s.setFilters);
  const filterCatalog = useQuery({ queryKey: ["filterCatalog", source?.id], enabled: !!source, queryFn: () => hibiki.sources.filterCatalog(source!.id) });
  const filterDefs = filterCatalog.data?.filters ?? [];
  const count = pickedCount(filterDefs, filters);
  const [filtersOpen, setFiltersOpen] = useState(false);
  // Applied when the sheet is done with, not at every tap (see useLiveFilters).
  const { draft, change, apply, reset } = useLiveFilters(filters, setFilters, true);
  const inputRef = useRef<HTMLInputElement>(null);

  const searching = longEnough || hasFilters;
  const showRecent = !searching && value.trim().length === 0 && recent.length > 0;

  return (
    <div className="min-h-full bg-app-bg px-4 pb-6">
      {/* Only while results are shown; settles below the search bar stuck to the top. */}
      <PullToRefresh onRefresh={refresh} disabled={!(longEnough || hasFilters) || !source} offset={68} />
      {/* Stays at the top while the results scroll - the page's scroll box already starts under the
          status bar, so its top edge is the right place. */}
      <div className="mobile-sticky-backdrop sticky top-0 z-20 -mx-4 bg-app-bg px-4 pb-3 pt-3">
        <div className="flex items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-muted" strokeWidth={2} />
            <input
              ref={inputRef}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key !== "Enter") return;
                inputRef.current?.blur();
                if (value.trim().length >= MIN_QUERY_LENGTH) addRecent(value.trim());
              }}
              autoFocus={!urlQuery}
              enterKeyHint="search"
              placeholder={t("catalog.searchPlaceholder")}
              aria-label={t("catalog.searchPlaceholder")}
              className="h-11 w-full rounded-full border border-border bg-app-popover pl-11 pr-10 text-[15px] text-text outline-none placeholder:text-muted focus:border-accent/50"
            />
            {value && (
              <button onClick={() => { setValue(""); inputRef.current?.focus(); }} aria-label={t("search.clear")} className="absolute right-1.5 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-muted active:bg-text/[.08]">
                <X className="h-4 w-4" strokeWidth={2.25} />
              </button>
            )}
          </div>
          {filterDefs.length > 0 && (
            <button onClick={() => setFiltersOpen(true)} aria-label={t("search.filters.button")} className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-border bg-app-popover text-text active:bg-text/[.08]">
              <SlidersHorizontal className="h-[18px] w-[18px]" strokeWidth={2} />
              {count > 0 && <span className="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-fg">{count}</span>}
            </button>
          )}
        </div>
      </div>

      {showRecent && (
        <div>
          <div className="mb-1 flex items-center justify-between px-1">
            <p className="text-[11px] font-bold uppercase tracking-wide text-muted">{t("search.recent")}</p>
            <button onClick={clearRecent} className="py-1 text-xs font-semibold text-muted">{t("search.clearRecent")}</button>
          </div>
          {recent.map((q) => (
            <div key={q} className="flex items-center">
              <button onClick={() => setValue(q)} className="flex min-w-0 flex-1 items-center gap-3 rounded-xl px-1 py-3 text-left text-[15px] text-text active:bg-text/[.06]">
                <Clock className="h-[18px] w-[18px] shrink-0 text-muted" strokeWidth={2} />
                <span className="truncate">{q}</span>
              </button>
              <button onClick={() => removeRecent(q)} aria-label={t("search.removeRecent")} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-muted active:bg-text/[.08]">
                <X className="h-4 w-4" strokeWidth={2} />
              </button>
            </div>
          ))}
        </div>
      )}
      {!searching && value.trim().length > 0 && <p className="py-10 text-center text-sm text-muted">{t("search.minChars", { count: MIN_QUERY_LENGTH })}</p>}

      {searching && (
        // Opening a result keeps the query among the recent ones.
        <div onClickCapture={() => { if (longEnough) addRecent(query); }}>
          {results.isLoading && <PosterGridSkeleton count={9} />}
          {results.isError && <ErrorBanner message={t("search.errorGeneric", { source: source?.name ?? t("search.source") })} error={results.error} />}
          {results.isSuccess && items.length === 0 && <div className="py-16 text-center text-sm text-muted">{longEnough ? t("search.empty", { query }) : t("search.filteredEmpty")}</div>}
          {items.length > 0 && <PosterGrid>{items.map((item) => <AnimeCard key={`${item.sourceId}:${item.id}`} anime={item} />)}</PosterGrid>}
          {results.hasNextPage && (
            <div ref={sentinel} className="mt-6 flex justify-center">
              <div className="h-7 w-7 animate-spin rounded-full border-2 border-border border-t-accent" />
            </div>
          )}
        </div>
      )}

      <BottomSheet
        open={filtersOpen}
        prewarm
        onClose={() => { apply(); setFiltersOpen(false); }}
        title={t("search.filters.button")}
        className="h-[85vh]"
        footer={
          <div className="flex items-center justify-between gap-2">
            <button onClick={reset} disabled={pickedCount(filterDefs, draft) === 0} className="rounded-full px-4 py-2.5 text-sm font-semibold text-muted active:bg-text/[.06] disabled:opacity-40">{t("search.filters.reset")}</button>
            <button onClick={() => { apply(); setFiltersOpen(false); }} className="rounded-full bg-text px-6 py-2.5 text-sm font-bold text-bg active:opacity-90">{t("search.filters.done")}</button>
          </div>
        }
      >
        <div className="px-3 pt-2">{filterCatalog.isLoading ? <p className="py-6 text-center text-sm text-muted">…</p> : <FilterSections defs={filterDefs} draft={draft} onChange={change} />}</div>
      </BottomSheet>
    </div>
  );
}
