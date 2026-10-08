// Device sync, the data half: what changed here since a point, and folding in what changed elsewhere.
//
// Every synced table carries a change number (change_seq) that triggers set on each write, from one
// counter per database (migration 0018). "Everything after N" is then a plain query, whoever made the
// change - this device, or another one whose change was applied here (it travels on, so a third
// device gets it too). Deletions leave a tombstone, written by the same triggers.
//
// Merging is per row and needs no shared clock beyond each change's own time:
// - library: the later category change wins (updated_at, set only when the category changes);
// - progress: the later save wins, but "watched" never goes back to unwatched;
// - ratings: the later rating wins;
// - xp: events are added once each (by uid);
// - daily activity: each device's own row per day only grows, so the larger value is the right one;
// - a deletion beats a change made before it, and loses to one made after.
import { and, asc, eq, gt, sql } from "drizzle-orm";
import { cachedAnime, dailyActivity, library, syncState, syncTombstones, titleRatings, watchProgress, xpEvents } from "../db/schema";
import { getPlatform } from "../platform";

const getDb = () => getPlatform().db.get();
const SEP = "\u001f";

export type LibraryRow = Omit<typeof library.$inferSelect, "changeSeq">;
export type ProgressRow = Omit<typeof watchProgress.$inferSelect, "changeSeq">;
export type RatingRow = Omit<typeof titleRatings.$inferSelect, "changeSeq">;
export type XpRow = Omit<typeof xpEvents.$inferSelect, "changeSeq" | "id">;
export type ActivityRow = Omit<typeof dailyActivity.$inferSelect, "changeSeq">;
export interface Tombstone {
  tbl: SyncedTable;
  key: string;
  deletedAt: number;
}
export type SyncedTable = "library" | "watch_progress" | "title_ratings" | "xp_events" | "daily_activity";

/** One batch of changes, in change-number order up to `upTo`. */
export interface ChangeSet {
  /** The highest change number this batch covers; the next batch asks for what comes after it. */
  upTo: number;
  /** More changes are waiting after `upTo`. */
  more: boolean;
  library: LibraryRow[];
  progress: ProgressRow[];
  ratings: RatingRow[];
  xp: XpRow[];
  activity: ActivityRow[];
  tombstones: Tombstone[];
  /**
   * The cached card data (name, poster, ...) of the titles this batch's progress and ratings are
   * about. Not a synced table - a cache rides along so the other device can draw those titles before,
   * or without, asking a source it may not have. Absent from batches of builds that predate it.
   */
  titles?: TitleCacheRow[];
}

export type TitleCacheRow = typeof cachedAnime.$inferSelect;

// Per table and batch. Progress rows can carry a frame thumbnail (tens of kilobytes), so fewer.
const BATCH_ROWS = 400;
const BATCH_PROGRESS_ROWS = 80;

export async function deviceId(): Promise<string> {
  const row = await getDb().select({ value: syncState.value }).from(syncState).where(eq(syncState.key, "device_id")).get();
  if (!row) throw new Error("This database has no device id - migration 0018 has not run");
  return row.value;
}

export async function currentSeq(): Promise<number> {
  const row = await getDb().select({ value: syncState.value }).from(syncState).where(eq(syncState.key, "seq")).get();
  return Number(row?.value ?? 0);
}

/**
 * The changes made after change number `since`, at most one batch of them.
 *
 * Each table is read up to its own limit in change order. When a table fills its limit, its last
 * change number is a cut: every table's rows past the lowest such cut wait for the next batch, so a
 * batch is always "everything up to `upTo`" and nothing is skipped between batches.
 */
export async function collectChanges(since: number): Promise<ChangeSet> {
  const db = getDb();
  const read = async <T extends { changeSeq: number }>(rows: T[] | Promise<T[]>, limit: number) => {
    const list = await rows;
    return { list, full: list.length >= limit, last: list.at(-1)?.changeSeq ?? 0 };
  };
  const [lib, prog, rat, xp, act, tomb] = await Promise.all([
    read(db.select().from(library).where(gt(library.changeSeq, since)).orderBy(asc(library.changeSeq)).limit(BATCH_ROWS).all(), BATCH_ROWS),
    read(db.select().from(watchProgress).where(gt(watchProgress.changeSeq, since)).orderBy(asc(watchProgress.changeSeq)).limit(BATCH_PROGRESS_ROWS).all(), BATCH_PROGRESS_ROWS),
    read(db.select().from(titleRatings).where(gt(titleRatings.changeSeq, since)).orderBy(asc(titleRatings.changeSeq)).limit(BATCH_ROWS).all(), BATCH_ROWS),
    read(db.select().from(xpEvents).where(gt(xpEvents.changeSeq, since)).orderBy(asc(xpEvents.changeSeq)).limit(BATCH_ROWS).all(), BATCH_ROWS),
    read(db.select().from(dailyActivity).where(gt(dailyActivity.changeSeq, since)).orderBy(asc(dailyActivity.changeSeq)).limit(BATCH_ROWS).all(), BATCH_ROWS),
    read(db.select().from(syncTombstones).where(gt(syncTombstones.changeSeq, since)).orderBy(asc(syncTombstones.changeSeq)).limit(BATCH_ROWS).all(), BATCH_ROWS),
  ]);
  const all = [lib, prog, rat, xp, act, tomb];
  const cuts = all.filter((part) => part.full).map((part) => part.last);
  const upTo = cuts.length > 0 ? Math.min(...cuts) : await currentSeq();
  const keep = <T extends { changeSeq: number }>(rows: T[]) => rows.filter((row) => row.changeSeq <= upTo);
  const strip = <T extends { changeSeq: number }>({ changeSeq: _seq, ...rest }: T) => rest;
  const progress = keep(prog.list).map(strip);
  const ratings = keep(rat.list).map(strip);
  return {
    upTo,
    more: cuts.length > 0,
    library: keep(lib.list).map(strip),
    progress,
    ratings,
    titles: await cachedTitlesFor([...progress.map((row) => ({ sourceId: row.sourceId, animeId: row.titleId })), ...ratings]),
    xp: keep(xp.list).map(({ id: _id, ...row }) => strip(row)),
    activity: keep(act.list).map(strip),
    tombstones: keep(tomb.list).map(({ tbl, key, deletedAt }) => ({ tbl: tbl as SyncedTable, key, deletedAt })),
  };
}

/** The cached cards of these titles, each once; titles never cached are left out. */
async function cachedTitlesFor(titles: Array<{ sourceId: string; animeId: string }>): Promise<TitleCacheRow[]> {
  const db = getDb();
  const seen = new Set<string>();
  const rows: TitleCacheRow[] = [];
  for (const { sourceId, animeId } of titles) {
    const key = `${sourceId}${SEP}${animeId}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const row = await db.select().from(cachedAnime).where(and(eq(cachedAnime.sourceId, sourceId), eq(cachedAnime.animeId, animeId))).get();
    if (row) rows.push(row);
  }
  return rows;
}

// --- Merge decisions (pure) -------------------------------------------------------------------------

/** The later of two versions by their own change time; the local one on a tie. */
export function newer<T>(local: T | undefined, incoming: T, time: (row: T) => number): boolean {
  return !local || time(incoming) > time(local);
}

/** Progress from two devices: the later save's position and choices, "watched" if either says so,
 * and a frame from whichever has one (the later one's if both). */
export function mergeProgress(local: ProgressRow | undefined, incoming: ProgressRow): ProgressRow | null {
  if (!local) return incoming;
  const later = incoming.updatedAt > local.updatedAt ? incoming : local;
  const merged: ProgressRow = {
    ...later,
    watched: local.watched || incoming.watched,
    thumbnailDataUrl: later.thumbnailDataUrl ?? (later === incoming ? local.thumbnailDataUrl : incoming.thumbnailDataUrl),
  };
  const same = merged.updatedAt === local.updatedAt && merged.watched === local.watched && merged.thumbnailDataUrl === local.thumbnailDataUrl;
  return same ? null : merged;
}

/** A deletion wins over a version that is not newer than it. */
export function deletedAfter(tombstoneAt: number | undefined, changedAt: number): boolean {
  return tombstoneAt !== undefined && tombstoneAt >= changedAt;
}

// --- Applying -------------------------------------------------------------------------------------

const libraryKey = (row: { sourceId: string; animeId: string }) => `${row.sourceId}${SEP}${row.animeId}`;
const progressKey = (row: { sourceId: string; titleId: string; episodeId: string }) => `${row.sourceId}${SEP}${row.titleId}${SEP}${row.episodeId}`;

async function tombstoneAt(tbl: SyncedTable, key: string): Promise<number | undefined> {
  const row = await getDb().select({ at: syncTombstones.deletedAt }).from(syncTombstones).where(and(eq(syncTombstones.tbl, tbl), eq(syncTombstones.key, key))).get();
  return row?.at;
}

/** Re-dates a tombstone the delete trigger just wrote "now" to the deletion's real time. */
async function keepDeletionTime(tbl: SyncedTable, key: string, deletedAt: number): Promise<void> {
  await getDb().update(syncTombstones).set({ deletedAt }).where(and(eq(syncTombstones.tbl, tbl), eq(syncTombstones.key, key))).run();
}

/**
 * Clears every synced table, for a replacement picked at pairing: this device's data gives way to
 * another's. The deletions are not kept as tombstones - they would travel to the other device and
 * delete what is about to come from it - so a third paired device keeps its copy and syncs it back.
 */
export async function wipeSyncedData(): Promise<void> {
  const db = getDb();
  for (const table of [library, watchProgress, titleRatings, xpEvents, dailyActivity]) await db.delete(table).run();
  await db.delete(syncTombstones).run();
}

/** A batch in a few words, for the log: "library 2, progress 14 (up to 913, more)". */
export function describeChanges(changes: ChangeSet): string {
  const parts = ([
    ["library", changes.library.length],
    ["progress", changes.progress.length],
    ["ratings", changes.ratings.length],
    ["xp", changes.xp.length],
    ["activity", changes.activity.length],
    ["deletions", changes.tombstones.length],
  ] as const).filter(([, count]) => count > 0).map(([name, count]) => `${name} ${count}`);
  return `${parts.join(", ") || "nothing"} (up to ${changes.upTo}${changes.more ? ", more" : ""})`;
}

export function changeCount(changes: ChangeSet): number {
  return changes.library.length + changes.progress.length + changes.ratings.length + changes.xp.length + changes.activity.length + changes.tombstones.length;
}

export interface ApplyResult {
  changed: number;
}

/** Folds another device's batch into this database. Returns how many rows actually changed here. */
export async function applyChanges(changes: ChangeSet): Promise<ApplyResult> {
  const db = getDb();
  let changed = 0;

  // Cards first, so whatever the rows below make appear can already be drawn. The fresher copy wins.
  for (const row of changes.titles ?? []) {
    const local = await db.select({ cachedAt: cachedAnime.cachedAt }).from(cachedAnime).where(and(eq(cachedAnime.sourceId, row.sourceId), eq(cachedAnime.animeId, row.animeId))).get();
    if (local && local.cachedAt >= row.cachedAt) continue;
    await db.insert(cachedAnime).values(row).onConflictDoUpdate({
      target: [cachedAnime.sourceId, cachedAnime.animeId],
      set: { animeJson: row.animeJson, cachedAt: row.cachedAt },
    }).run();
  }

  for (const row of changes.library) {
    const local = await db.select().from(library).where(and(eq(library.sourceId, row.sourceId), eq(library.animeId, row.animeId))).get();
    if (deletedAfter(await tombstoneAt("library", libraryKey(row)), row.updatedAt)) continue;
    if (!newer(local, row, (r) => r.updatedAt)) continue;
    await db.insert(library).values(row).onConflictDoUpdate({
      target: [library.sourceId, library.animeId],
      set: { category: row.category, animeJson: row.animeJson, addedAt: row.addedAt, updatedAt: row.updatedAt },
    }).run();
    changed++;
  }

  for (const row of changes.progress) {
    if (deletedAfter(await tombstoneAt("watch_progress", progressKey(row)), row.updatedAt)) continue;
    const local = await db.select().from(watchProgress).where(and(eq(watchProgress.sourceId, row.sourceId), eq(watchProgress.titleId, row.titleId), eq(watchProgress.episodeId, row.episodeId))).get();
    const merged = mergeProgress(local ? stripSeq(local) : undefined, row);
    if (!merged) continue;
    const { sourceId: _s, titleId: _t, episodeId: _e, ...fields } = merged;
    await db.insert(watchProgress).values(merged).onConflictDoUpdate({
      target: [watchProgress.sourceId, watchProgress.titleId, watchProgress.episodeId],
      set: fields,
    }).run();
    changed++;
  }

  for (const row of changes.ratings) {
    const local = await db.select().from(titleRatings).where(and(eq(titleRatings.sourceId, row.sourceId), eq(titleRatings.animeId, row.animeId))).get();
    if (deletedAfter(await tombstoneAt("title_ratings", libraryKey(row)), row.ratedAt)) continue;
    if (!newer(local, row, (r) => r.ratedAt)) continue;
    await db.insert(titleRatings).values(row).onConflictDoUpdate({
      target: [titleRatings.sourceId, titleRatings.animeId],
      set: { rating: row.rating, ratedAt: row.ratedAt },
    }).run();
    changed++;
  }

  for (const row of changes.xp) {
    if (!row.uid || (await tombstoneAt("xp_events", row.uid)) !== undefined) continue;
    const exists = await db.select({ id: xpEvents.id }).from(xpEvents).where(eq(xpEvents.uid, row.uid)).get();
    if (exists) continue;
    await db.insert(xpEvents).values(row).run();
    changed++;
  }

  for (const row of changes.activity) {
    const local = await db.select().from(dailyActivity).where(and(eq(dailyActivity.date, row.date), eq(dailyActivity.deviceId, row.deviceId))).get();
    const watchedMs = Math.max(local?.watchedMs ?? 0, row.watchedMs);
    const completedCount = Math.max(local?.completedCount ?? 0, row.completedCount);
    if (local && local.watchedMs === watchedMs && local.completedCount === completedCount) continue;
    await db.insert(dailyActivity).values({ date: row.date, deviceId: row.deviceId, watchedMs, completedCount }).onConflictDoUpdate({
      target: [dailyActivity.date, dailyActivity.deviceId],
      set: { watchedMs, completedCount },
    }).run();
    changed++;
  }

  for (const tomb of changes.tombstones) {
    const parts = tomb.key.split(SEP);
    let deleted = false;
    if (tomb.tbl === "library" && parts.length === 2) {
      const local = await db.select({ updatedAt: library.updatedAt }).from(library).where(and(eq(library.sourceId, parts[0]), eq(library.animeId, parts[1]))).get();
      if (local && deletedAfter(tomb.deletedAt, local.updatedAt)) {
        await db.delete(library).where(and(eq(library.sourceId, parts[0]), eq(library.animeId, parts[1]))).run();
        deleted = true;
      }
    } else if (tomb.tbl === "watch_progress" && parts.length === 3) {
      const where = and(eq(watchProgress.sourceId, parts[0]), eq(watchProgress.titleId, parts[1]), eq(watchProgress.episodeId, parts[2]));
      const local = await db.select({ updatedAt: watchProgress.updatedAt }).from(watchProgress).where(where).get();
      if (local && deletedAfter(tomb.deletedAt, local.updatedAt)) {
        await db.delete(watchProgress).where(where).run();
        deleted = true;
      }
    } else if (tomb.tbl === "title_ratings" && parts.length === 2) {
      const where = and(eq(titleRatings.sourceId, parts[0]), eq(titleRatings.animeId, parts[1]));
      const local = await db.select({ ratedAt: titleRatings.ratedAt }).from(titleRatings).where(where).get();
      if (local && deletedAfter(tomb.deletedAt, local.ratedAt)) {
        await db.delete(titleRatings).where(where).run();
        deleted = true;
      }
    } else if (tomb.tbl === "xp_events") {
      const local = await db.select({ id: xpEvents.id }).from(xpEvents).where(eq(xpEvents.uid, tomb.key)).get();
      if (local) {
        await db.delete(xpEvents).where(eq(xpEvents.id, local.id)).run();
        deleted = true;
      }
    }
    if (deleted) {
      await keepDeletionTime(tomb.tbl, tomb.key, tomb.deletedAt);
      changed++;
    } else if ((await tombstoneAt(tomb.tbl, tomb.key)) === undefined) {
      // Nothing here to delete (never synced, or already gone): remember the deletion anyway, so a
      // stale copy arriving later from somewhere else is not resurrected.
      await db.insert(syncTombstones).values({ tbl: tomb.tbl, key: tomb.key, deletedAt: tomb.deletedAt }).run();
      await bumpTombstone(tomb.tbl, tomb.key);
    }
  }

  return { changed };
}

function stripSeq<T extends { changeSeq: number }>({ changeSeq: _seq, ...rest }: T): Omit<T, "changeSeq"> {
  return rest;
}

/** A tombstone written directly (not by a delete trigger) still needs a change number to travel on. */
async function bumpTombstone(tbl: SyncedTable, key: string): Promise<void> {
  const db = getDb();
  // Through the update builder: it names the column bare in SET. A raw `SET "sync_state"."value" =`
  // is a syntax error to the older SQLite on Android (newer ones accept it, so tests did not notice).
  await db.update(syncState).set({ value: sql`CAST("value" AS INTEGER) + 1` }).where(eq(syncState.key, "seq")).run();
  const seq = await currentSeq();
  await db.update(syncTombstones).set({ changeSeq: seq }).where(and(eq(syncTombstones.tbl, tbl), eq(syncTombstones.key, key))).run();
}
