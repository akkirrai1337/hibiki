// New-episode notices, the decisions alone: is there a new episode in the dub this title is watched
// in, and when to look next. Pure, so it is tested without a source or a clock (checker.ts does the
// asking and the storing).
//
// When to look, per title - a date is a hint, never a requirement:
//  - the source says when the next episode airs: half an hour after it, then 2, 6, 12 and 24 hours
//    after it (a dub follows the airing by hours or days), then as if it had said nothing;
//  - no date, but episodes have come out before: their usual interval (median of the last gaps,
//    1 to 30 days) says when the next is due - every few hours around then, daily otherwise;
//  - nothing to go by (a title just added): every 12 hours;
//  - a long silence (three usual intervals, or three weeks) is a break: daily, then every three days
//    after two months;
//  - out overall but not yet in the watched dub: 2, 6, 12, 24 hours after it came out, then daily.
// A failed check is not news: it is retried later, longer each time, up to a day.

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const AFTER_AIRING = [0.5 * HOUR, 2 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR];
const DUB_LADDER = [2 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR];
const NO_HISTORY = 12 * HOUR;
const AROUND_DUE = 4 * HOUR;
const MAX_ARRIVALS = 8;

export interface EpisodeLike {
  id: string;
  number: number;
}

export interface GroupLike {
  id: string;
  title: string;
  episodes: EpisodeLike[];
}

/** What is remembered about one title between checks. */
export interface WatchState {
  /** The dub followed (the group of the last episode watched); null = any. */
  groupId: string | null;
  /** The released count the details gave last time - a cheap "anything new?" (null: none given). */
  knownCount: number | null;
  /** The highest episode number seen in the followed dub; null until the first check. */
  knownMax: number | null;
  /** When episodes came out (ms), newest last, for the usual interval. */
  arrivals: number[];
  /** Out overall but not yet in the followed dub: since when (ms). */
  waitingSince: number | null;
  nextEpisodeAt: number | null;
  failures: number;
}

export interface NewEpisodes {
  /** The followed group, or the one the new episode turned up in when any dub will do. */
  group: GroupLike;
  /** Numbers of the episodes new since last time, ascending. */
  numbers: number[];
  /** The first of them, to open from the notice. */
  firstEpisodeId: string;
}

function maxNumber(group: GroupLike | undefined): number | null {
  if (!group || group.episodes.length === 0) return null;
  return Math.max(...group.episodes.map((episode) => episode.number));
}

/**
 * The episodes new in the followed dub since `knownMax`, or null. With no followed dub (or it is
 * gone), whichever group has gone furthest counts. The very first look (knownMax null) only learns
 * where things stand. Episodes already watched (another device, via sync) are no news.
 */
export function findNew(groups: GroupLike[], groupId: string | null, knownMax: number | null, watched: ReadonlySet<number>): { max: number | null; found: NewEpisodes | null } {
  const followed = groupId ? groups.find((group) => group.id === groupId) : undefined;
  const candidates = followed ? [followed] : groups;
  let best: GroupLike | undefined;
  for (const group of candidates) if ((maxNumber(group) ?? -1) > (maxNumber(best) ?? -1)) best = group;
  const max = maxNumber(best);
  if (!best || max === null || knownMax === null || max <= knownMax) return { max, found: null };
  const fresh = best.episodes
    .filter((episode) => episode.number > knownMax && !watched.has(episode.number))
    .sort((a, b) => a.number - b.number);
  if (fresh.length === 0) return { max, found: null };
  return { max, found: { group: best, numbers: [...new Set(fresh.map((episode) => episode.number))], firstEpisodeId: fresh[0].id } };
}

/** Whether the light details already say nothing has come out, so the episode list is not asked. */
export function nothingNewByDetails(state: WatchState, count: number | null): boolean {
  return count !== null && state.knownCount !== null && count <= state.knownCount && state.waitingSince === null;
}

/** The usual gap between episodes coming out, from the remembered arrivals; null with fewer than two. */
export function usualInterval(arrivals: number[]): number | null {
  if (arrivals.length < 2) return null;
  const gaps = arrivals.slice(1).map((at, i) => at - arrivals[i]).filter((gap) => gap > HOUR).sort((a, b) => a - b);
  if (gaps.length === 0) return null;
  const median = gaps[Math.floor(gaps.length / 2)];
  return Math.min(30 * DAY, Math.max(DAY, median));
}

export function withArrival(arrivals: number[], at: number): number[] {
  return [...arrivals, at].slice(-MAX_ARRIVALS);
}

/** When to look at this title next. */
export function nextCheckAt(state: WatchState, now: number, outcome: "new" | "nothing" | "waiting" | "failed"): number {
  if (outcome === "failed") return now + Math.min(DAY, HOUR * 2 ** Math.min(5, state.failures));

  if (state.waitingSince !== null) {
    const step = DUB_LADDER.find((delay) => state.waitingSince! + delay > now);
    return step !== undefined ? state.waitingSince + step : now + DAY;
  }

  if (state.nextEpisodeAt !== null) {
    if (state.nextEpisodeAt > now) return Math.min(state.nextEpisodeAt + AFTER_AIRING[0], now + 3 * DAY);
    const step = AFTER_AIRING.find((delay) => state.nextEpisodeAt! + delay > now);
    if (step !== undefined) return state.nextEpisodeAt + step;
  }

  const interval = usualInterval(state.arrivals);
  const last = state.arrivals[state.arrivals.length - 1];
  if (interval === null || last === undefined) return now + NO_HISTORY;
  const silence = now - last;
  if (silence > 60 * DAY) return now + 3 * DAY;
  if (silence > Math.max(3 * interval, 21 * DAY)) return now + DAY;
  const due = last + interval;
  if (now < due - 6 * HOUR) return Math.min(now + DAY, due - 6 * HOUR);
  if (now < due + 2 * DAY) return now + AROUND_DUE;
  return now + DAY;
}

/** Whether a title has nothing more to come: the source says it finished, or all of it is out. */
export function isFinished(status: string | null | undefined, released: number | null, total: number | null): boolean {
  if (status && /^(released|completed|finished)$/i.test(status)) return true;
  return released !== null && total !== null && total > 0 && released >= total;
}
