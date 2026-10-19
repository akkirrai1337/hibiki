// A title's details arrive in two parts from a source that supports it: the title itself, which
// is what a page needs to draw, and its extras ("similar titles"), which come from a request of their
// own and only decorate the page. The host asks for the title first, shows it, and the extras after,
// so a slow extras request never holds the page back (runtime.getTitleCore / getTitleExtras).
//
// The title cache (offlineCache.ts) holds the whole of it, as recommendations read a watched
// title's similar list from there. These two functions keep the halves together.
import type { AnimeTitle, TitleExtras } from "@shared/types";

/**
 * A title that came without its extras (`extrasPending`), with the extras an earlier copy held put
 * back, so a revisit shows the similar titles at once while fresh ones are fetched, and the cache is
 * not left without them. `complete` says an earlier copy had them: the cache copy then no longer
 * waits for any (see forCache). The page's copy stays pending, so the extras are fetched again.
 */
export function mergeKnownExtras(core: AnimeTitle, previous: AnimeTitle | null): { title: AnimeTitle; complete: boolean } {
  if (core.extrasPending !== true) return { title: core, complete: true };
  if (!previous || previous.extrasPending === true) return { title: core, complete: false };
  return { title: { ...core, similarAnime: previous.similarAnime ?? [] }, complete: true };
}

/** What goes in the cache: the title, no longer marked as waiting when its extras are in it. */
export function forCache(title: AnimeTitle, complete: boolean): AnimeTitle {
  if (!complete || title.extrasPending !== true) return title;
  const { extrasPending: _pending, ...settled } = title;
  return settled;
}

/** The title with its extras in, done waiting. */
export function withExtras(title: AnimeTitle, extras: TitleExtras): AnimeTitle {
  const { extrasPending: _pending, ...settled } = title;
  return { ...settled, similarAnime: extras.similarAnime ?? [] };
}
