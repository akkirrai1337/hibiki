// New-episode notices: which titles are watched, asking their sources when it is time, and what
// to tell the person. The decisions themselves are logic.ts.
//
// Watched: every ongoing title in "Watching", plus any title whose bell is on, minus any whose bell
// is off. Each check is two steps, so a title with nothing new costs only its light details:
//  1. the details (released episode count, next date, status) - unchanged count, nothing to do;
//  2. otherwise the episode list (from the title's snapshot when it still holds - titleSnapshots.ts),
//     looked at in the dub the last episode was watched in.
import { and, desc, eq, isNotNull } from "drizzle-orm";
import type { AnimeTitle, EpisodeBell, NewEpisodeNotice, PlaybackGroup } from "@shared/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import { episodeWatch, library, watchProgress } from "../db/schema";
import { cacheAnime, getCachedAnime } from "../offlineCache";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { findNew, isFinished, nextCheckAt, nothingNewByDetails, withArrival, type WatchState } from "./logic";

const getDb = () => getPlatform().db.get();
/** A title that has finished is not looked at again (its bell can still be turned on by hand). */
const NEVER = Number.MAX_SAFE_INTEGER;
/** At most this many titles per run: a run has to fit in the few minutes it is given. */
const PER_RUN = 12;

type Row = typeof episodeWatch.$inferSelect;
type Runtime = Pick<ExtensionRuntime, "getById" | "getPlaybackGroups" | "list" | "hasPlayableLink">;

const toState = (row: Row): WatchState => ({
  groupId: row.groupId,
  knownCount: row.knownCount,
  knownMax: row.knownMax,
  arrivals: (() => {
    try {
      const parsed = JSON.parse(row.arrivals) as unknown;
      return Array.isArray(parsed) ? parsed.filter((at): at is number => typeof at === "number") : [];
    } catch {
      return [];
    }
  })(),
  waitingSince: row.waitingSince,
  nextEpisodeAt: row.nextEpisodeAt,
  failures: row.failures,
});

function titleOf(anime: AnimeTitle | null, fallback: string): string {
  return anime?.russianName || anime?.englishName || anime?.originalName || fallback;
}

/** Still to have new episodes, by what is known of it (the details cached last, else the library's
 * own copy): an ongoing, or an announced title whose first episode is still to come. */
function isOngoing(anime: AnimeTitle | null): boolean {
  if (!anime) return false;
  return !isFinished(anime.status ?? null, anime.availableEpisodeCount ?? null, anime.episodeCount ?? null);
}

async function rowFor(sourceId: string, animeId: string): Promise<Row | undefined> {
  return getDb().select().from(episodeWatch).where(and(eq(episodeWatch.sourceId, sourceId), eq(episodeWatch.animeId, animeId))).get();
}

/** The titles watched for new episodes right now, each with a state row (made due at once if new). */
async function watchedTitles(runtime: Runtime, now: number): Promise<Array<{ row: Row; anime: AnimeTitle | null }>> {
  const installed = new Set(runtime.list().map((source) => source.id));
  const rows = await getDb().select().from(episodeWatch).all();
  const bySource = new Map(rows.map((row) => [`${row.sourceId}\n${row.animeId}`, row]));
  const watching = await getDb().select().from(library).where(eq(library.category, "watching")).all();
  const result: Array<{ row: Row; anime: AnimeTitle | null }> = [];
  const seen = new Set<string>();
  const consider = async (sourceId: string, animeId: string, libraryJson: string | null) => {
    const key = `${sourceId}\n${animeId}`;
    if (seen.has(key) || !installed.has(sourceId)) return;
    seen.add(key);
    const existing = bySource.get(key);
    if (existing?.bell === "off") return;
    const anime = (await getCachedAnime(sourceId, animeId).catch(() => null)) ?? (libraryJson ? (JSON.parse(libraryJson) as AnimeTitle) : null);
    if (existing?.bell !== "on" && !isOngoing(anime)) return;
    let row = existing;
    if (!row) {
      await getDb().insert(episodeWatch).values({ sourceId, animeId, nextCheckAt: now }).onConflictDoNothing().run();
      row = await rowFor(sourceId, animeId);
    }
    if (row) result.push({ row, anime });
  };
  for (const entry of watching) await consider(entry.sourceId, entry.animeId, entry.animeJson);
  for (const row of rows) if (row.bell === "on") await consider(row.sourceId, row.animeId, null);
  return result;
}

async function followedGroup(sourceId: string, animeId: string): Promise<string | null> {
  const last = await getDb()
    .select({ groupId: watchProgress.groupId })
    .from(watchProgress)
    .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, animeId), isNotNull(watchProgress.groupId)))
    .orderBy(desc(watchProgress.updatedAt))
    .limit(1)
    .get();
  return last?.groupId ?? null;
}

async function watchedNumbers(sourceId: string, animeId: string): Promise<Set<number>> {
  const rows = await getDb()
    .select({ number: watchProgress.episodeNumber })
    .from(watchProgress)
    .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, animeId), eq(watchProgress.watched, true)))
    .all();
  return new Set(rows.map((row) => row.number));
}

/**
 * findNew, but only in a dub where the new episode can be played: the followed dub, or - with none
 * followed - the furthest dubs in turn. An episode that is out only on players the app cannot play
 * (YummyAnime's CVH-only dubs) is not news yet; `max` still records how far the source has got.
 */
async function findPlayableNew(
  runtime: Runtime,
  sourceId: string,
  animeId: string,
  groups: PlaybackGroup[],
  groupId: string | null,
  knownMax: number | null,
  watched: ReadonlySet<number>,
): Promise<ReturnType<typeof findNew>> {
  const overall = findNew(groups, groupId, knownMax, watched);
  if (!overall.found) return overall;
  const followed = groupId ? groups.find((group) => group.id === groupId) : undefined;
  const furthest = (group: PlaybackGroup) => Math.max(-1, ...group.episodes.map((episode) => episode.number));
  const candidates = followed ? [followed] : [...groups].sort((a, b) => furthest(b) - furthest(a));
  for (const group of candidates) {
    const { found } = findNew([group], group.id, knownMax, watched);
    if (!found) continue;
    const playable = await runtime.hasPlayableLink(sourceId, animeId, group.id, found.firstEpisodeId).catch(() => false);
    if (playable) return { max: overall.max, found };
    logger.info("episodes", `${sourceId}/${animeId}: episode ${found.numbers[0]} in ${group.id} has no playable link yet`);
  }
  // Out, but nowhere playable: known that far only up to what was known before, so it is looked for again.
  return { max: knownMax, found: null };
}

/** Epoch ms, whatever the source sent (some send seconds). */
const asMs = (at: number | null | undefined) => (at == null || at <= 0 ? null : at < 1e12 ? at * 1000 : at);

async function checkOne(runtime: Runtime, row: Row, now: number): Promise<NewEpisodeNotice | null> {
  const { sourceId, animeId } = row;
  const state = toState(row);
  state.groupId = await followedGroup(sourceId, animeId);
  let notice: NewEpisodeNotice | null = null;
  let outcome: "new" | "nothing" | "waiting" | "failed" = "nothing";
  let finished = false;
  try {
    const details = await runtime.getById(sourceId, animeId);
    // Cached as the title page would: the snapshot's freshness is read from it.
    await cacheAnime(sourceId, animeId, details).catch(() => undefined);
    const count = details.availableEpisodeCount ?? null;
    state.nextEpisodeAt = asMs(details.nextEpisodeAt);
    const countRose = count !== null && state.knownCount !== null && count > state.knownCount;
    if (countRose) state.arrivals = withArrival(state.arrivals, now);
    if (state.knownMax === null || !nothingNewByDetails(state, count)) {
      const groups: PlaybackGroup[] = await runtime.getPlaybackGroups(sourceId, animeId);
      const { max, found } = await findPlayableNew(runtime, sourceId, animeId, groups, state.groupId, state.knownMax, await watchedNumbers(sourceId, animeId));
      if (state.knownMax === null || !found) {
        if (max !== null && (state.knownMax === null || max > state.knownMax)) state.knownMax = max;
        // Out overall (the count rose) but not in the dub followed: wait for it on its own ladder.
        if (!found && countRose && state.groupId !== null) {
          state.waitingSince ??= now;
          outcome = "waiting";
        }
      } else {
        state.knownMax = Math.max(...found.numbers);
        state.waitingSince = null;
        if (!countRose && count === null) state.arrivals = withArrival(state.arrivals, now);
        outcome = "new";
        notice = {
          sourceId,
          animeId,
          title: titleOf(details, animeId),
          posterUrl: details.posterUrl ?? null,
          groupId: found.group.id,
          groupTitle: found.group.title,
          episodeId: found.firstEpisodeId,
          numbers: found.numbers,
        };
      }
    }
    if (count !== null) state.knownCount = count;
    finished = isFinished(details.status ?? null, count, details.episodeCount ?? null) && state.waitingSince === null;
    state.failures = 0;
  } catch (error) {
    outcome = "failed";
    state.failures += 1;
    logger.warn("episodes", `${sourceId}/${animeId}: check failed (${error instanceof Error ? error.message : String(error)})`);
  }
  const next = finished && row.bell !== "on" ? NEVER : nextCheckAt(state, now, outcome);
  await getDb()
    .update(episodeWatch)
    .set({
      groupId: state.groupId,
      knownCount: state.knownCount,
      knownMax: state.knownMax,
      arrivals: JSON.stringify(state.arrivals),
      waitingSince: state.waitingSince,
      nextEpisodeAt: state.nextEpisodeAt,
      failures: state.failures,
      nextCheckAt: next,
      checkedAt: now,
    })
    .where(and(eq(episodeWatch.sourceId, sourceId), eq(episodeWatch.animeId, animeId)))
    .run();
  logger.info(
    "episodes",
    `${sourceId}/${animeId}: ${outcome}${notice ? ` (episodes ${notice.numbers.join(", ")})` : ""}, known up to ${state.knownMax ?? "?"}` +
      `${state.groupId ? ` in ${state.groupId}` : ""}, next check ${next === NEVER ? "never (finished)" : new Date(next).toISOString()}`,
  );
  return notice;
}

let running: Promise<NewEpisodeNotice[]> | null = null;

/** Checks every watched title whose time has come; the notices for what came out. One run at a time. */
export function checkDue(runtime: Runtime, now = Date.now()): Promise<NewEpisodeNotice[]> {
  running ??= (async () => {
    try {
      const titles = await watchedTitles(runtime, now);
      const due = titles.filter(({ row }) => row.nextCheckAt <= now).sort((a, b) => a.row.nextCheckAt - b.row.nextCheckAt).slice(0, PER_RUN);
      if (due.length > 0) logger.info("episodes", `checking ${due.length} of ${titles.length} watched title(s)`);
      const notices: NewEpisodeNotice[] = [];
      for (const { row } of due) {
        const notice = await checkOne(runtime, row, now);
        if (notice) notices.push(notice);
      }
      return notices;
    } finally {
      running = null;
    }
  })();
  return running;
}

/** The title page's bell: "auto" follows the library (an ongoing in "Watching"). */
export async function getBell(sourceId: string, animeId: string): Promise<EpisodeBell> {
  const row = await rowFor(sourceId, animeId);
  return row?.bell === "on" || row?.bell === "off" ? row.bell : "auto";
}

export async function setBell(sourceId: string, animeId: string, bell: EpisodeBell): Promise<void> {
  const value = bell === "auto" ? null : bell;
  await getDb()
    .insert(episodeWatch)
    .values({ sourceId, animeId, bell: value, nextCheckAt: Date.now() })
    .onConflictDoUpdate({ target: [episodeWatch.sourceId, episodeWatch.animeId], set: { bell: value, nextCheckAt: Date.now() } })
    .run();
}
