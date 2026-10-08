import { useMemo, useRef } from "react";
import { keepPreviousData, useQueries, useQuery } from "@tanstack/react-query";
import { hibiki } from "@/lib/hibiki";
import type { AnimeTitle, WatchProgress } from "@shared/types";

const RECENT_FETCH_LIMIT = 12;

export interface ContinueWatchingSlot {
  progress: WatchProgress;
  // undefined = title metadata still loading, null = resolved but failed (dead source, removed
  // title, ...) and should be skipped, AnimeTitle = ready to render.
  anime: AnimeTitle | null | undefined;
}

// Shared by the home page, the profile page's own "continue watching" row, and the full history
// page (a bigger `limit`) - they show the same underlying data, so this fetches it once per limit
// under query keys every caller agrees on: react-query's cache means whichever page loads first
// does the actual work for that limit, and another caller asking for the same limit reads it back
// instantly instead of re-running its own redundant getById calls per title. The limit is part of
// the key (not a separate query) so a delete's `invalidateQueries({queryKey: ["recent-progress"]})`
// (a prefix match) still reaches every limit currently in use, not just one.
/** How long a cached title stays good enough to draw a card from without asking the source again.
 * A day, because the fields a card shows don't change - this is "check back eventually in case a
 * source rewrote a title's name or artwork", not a freshness requirement. */
const CARD_METADATA_STALE_MS = 24 * 60 * 60_000;

/** Distinct titles among a set of progress rows, in the order they first appear. */
function distinctTitles(rows: WatchProgress[]): WatchProgress[] {
  const seen = new Set<string>();
  return rows.filter((row) => {
    const key = `${row.sourceId}:${row.titleId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/**
 * Title metadata for a set of progress rows, keyed "sourceId:titleId".
 *
 * Shared by both hooks below deliberately: they each used to carry their own copy of this, which
 * is exactly how the on-disk cache ended up wired into one of them and not the other. A card shows
 * four things - name, poster, type, year - and none of them change over a title's life, while
 * fetching them costs a full getById: three requests and ~56KB per card, on every launch.
 *
 * So the cache is read first and handed to react-query as initialData *with the time it was
 * written*, letting react-query's own staleness rule decide: a recent entry is used as-is and no
 * request is made at all, an older one refetches exactly as before. Measured with twelve cards:
 * twelve getById calls before, zero when the cache is fresh, and still twelve when it is stale.
 *
 * The queries are not created until the lookup has answered - initialData is read once, when a
 * query is first created, so building them while the lookup was in flight had every one of them
 * start a request and then receive the cached copy far too late to matter. That lookup is a local
 * SQLite read behind one IPC hop; waiting a frame for it is the whole difference.
 *
 * The detail page is untouched by any of this. It keys its own query separately and always fetches
 * live, so nothing that genuinely changes - episode counts, related titles - is ever read here.
 */
function useCardTitles(rows: WatchProgress[]): Record<string, AnimeTitle | null | undefined> {
  const unique = useMemo(() => distinctTitles(rows), [rows]);
  const keys = unique.map((row) => `${row.sourceId}:${row.titleId}`);
  const cached = useQuery({
    queryKey: ["cached-titles", keys.join(",")],
    queryFn: () => hibiki.sources.cachedTitles(unique.map((row) => ({ sourceId: row.sourceId, animeId: row.titleId }))),
    enabled: unique.length > 0,
    staleTime: Infinity,
    // A key with no answer yet used to empty everything below, un-creating every per-title query and
    // turning the whole row into skeletons until the new lookup landed - on "delete watch data" that
    // happened twice, once for the removal and again when the refetch pulled in the next-oldest title.
    // Holding the previous answer keeps the surviving cards on screen (see `active`).
    placeholderData: keepPreviousData,
  });
  // Titles the last real (non-placeholder) answer was fetched for.
  const answeredKeys = useRef<Set<string>>(new Set());
  if (cached.isSuccess && !cached.isPlaceholderData) answeredKeys.current = new Set(keys);
  const cachedTitles = cached.data;
  // A title's query is only created once a lookup that covers it has answered - initialData is read
  // once, when a query is first created, so creating it earlier means fetching live instead of using
  // the cache. Every title the previous answer covered is ready right away; a new one waits for its own
  // lookup, and until then is the only skeleton in the row.
  const active = unique.filter((row) => cachedTitles && (!cached.isPlaceholderData || answeredKeys.current.has(`${row.sourceId}:${row.titleId}`)));

  const queries = useQueries({
    queries: active.map((row) => {
      const key = `${row.sourceId}:${row.titleId}`;
      return {
        // Same key the detail and watch pages fetch a title's full record under (["anime",
        // sourceId, id]) - not a card-only key of its own. Clicking straight from this row into
        // an episode used to skip the detail page's own warm cache entirely, so the watch page's
        // title bar sat blank for a beat while it redid the exact getById this row just made.
        queryKey: ["anime", row.sourceId, row.titleId],
        // Each title resolves independently - a slow or dead source shouldn't block the others.
        queryFn: async (): Promise<AnimeTitle | null> => {
          try {
            return await hibiki.sources.getById(row.sourceId, row.titleId);
          } catch {
            return null;
          }
        },
        initialData: cachedTitles?.[key]?.title,
        initialDataUpdatedAt: cachedTitles?.[key]?.cachedAt,
        staleTime: CARD_METADATA_STALE_MS,
      };
    }),
  });

  const result: Record<string, AnimeTitle | null | undefined> = {};
  unique.forEach((row) => {
    const index = active.indexOf(row);
    result[`${row.sourceId}:${row.titleId}`] = index >= 0 ? queries[index]?.data : undefined;
  });
  return result;
}

export function useContinueWatching(limit: number = RECENT_FETCH_LIMIT): { slots: ContinueWatchingSlot[]; hasHistory: boolean } {
  const recent = useQuery({ queryKey: ["recent-progress", limit], queryFn: () => hibiki.progress.listRecent(limit) });
  // Progress is tracked per episode, so recently-watched episodes of the same title show up as
  // separate rows - dedupe on the title identity alone, before firing any network calls.
  const recentUnique = useMemo(() => distinctTitles(recent.data ?? []), [recent.data]);
  const titles = useCardTitles(recentUnique);
  // Cheap enough (12 items, no work per item) to just recompute on every render rather than reach
  // for useMemo with an awkward dependency key over the per-item query results.
  const slots = recentUnique.map((progress) => ({ progress, anime: titles[`${progress.sourceId}:${progress.titleId}`] }));
  return { slots, hasHistory: !!recent.data?.length };
}

export interface WatchHistoryEntry {
  progress: WatchProgress;
  anime: AnimeTitle | null | undefined;
}

// The history page's own feed (routes/history.tsx) - same ["recent-progress", limit] query as
// useContinueWatching above (same cache entry at a shared limit, same delete-invalidation prefix),
// but deliberately NOT deduped by title: every episode with saved progress gets its own entry,
// since the point of this page is "which episodes did I stop on", not "which titles". Anime
// metadata is still fetched once per unique title (no reason to refetch it per episode).
export function useWatchHistory(limit: number): { entries: WatchHistoryEntry[]; loading: boolean } {
  const recent = useQuery({ queryKey: ["recent-progress", limit], queryFn: () => hibiki.progress.listRecent(limit) });
  const rows = recent.data ?? [];
  const titles = useCardTitles(rows);
  // Plain recompute every render, same reasoning as useContinueWatching's `slots` above.
  const entries = rows.map((progress) => ({ progress, anime: titles[`${progress.sourceId}:${progress.titleId}`] }));
  // `loading` lets the page tell "nothing yet" from "nothing at all" instead of flashing the latter.
  return { entries, loading: recent.isLoading };
}
