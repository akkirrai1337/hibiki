import type { AnimeTitle } from "@shared/types";

/**
 * A title the app has already been handed by a *list* - a catalog page, a search result, a home
 * row - looked up out of whatever those queries currently hold.
 *
 * Why this is worth doing: a source's list methods return the same AnimeTitle shape getById does,
 * only with fewer fields filled in. Compared field by field against the live API, a search result
 * and a getById result for the same title agree on all fourteen fields the list carries - name,
 * poster, description, genres, ratings, status, type, year - and disagree on none. The nine that
 * only getById knows (episode counts, studios, screenshots, related/similar titles) are simply
 * absent. So the list result is a strict subset, never a contradiction, which is exactly what makes
 * it safe to draw first.
 *
 * Used as react-query `placeholderData`, never `setQueryData`: a placeholder is not treated as
 * fetched data, so the real getById still runs and fills in the rest. Seeding the cache with it
 * instead would leave a detail page permanently missing its episode list, which is the one thing
 * that page exists for.
 */

/** Both shapes a list query can hold: a plain array, or useInfiniteQuery's `{ pages: [...] }`. */
function itemsOf(data: unknown): unknown[] {
  if (Array.isArray(data)) return data;
  if (data && typeof data === "object" && Array.isArray((data as { pages?: unknown }).pages)) {
    return (data as { pages: unknown[] }).pages.flatMap((page) => (Array.isArray(page) ? page : []));
  }
  return [];
}

function isTitleFor(value: unknown, sourceId: string, animeId: string): value is AnimeTitle {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<AnimeTitle>;
  // sourceId is stamped onto every result by the runtime (see execute.ts's tagSource), so a title
  // from one source can never be matched against an identical id in another.
  return candidate.id === animeId && candidate.sourceId === sourceId;
}

/**
 * Scans the given query payloads for that title. Takes the raw data rather than a QueryClient so
 * the matching is testable on its own, and so this doesn't care which keys lists happen to live
 * under - a new list screen is covered without being registered anywhere.
 */
export function findListedTitle(payloads: Iterable<unknown>, sourceId: string, animeId: string): AnimeTitle | undefined {
  for (const payload of payloads) {
    for (const item of itemsOf(payload)) {
      if (isTitleFor(item, sourceId, animeId)) return item;
    }
  }
  return undefined;
}
