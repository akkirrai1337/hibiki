import { eq, and, gte, desc, sql } from "drizzle-orm";
import type { HibikiApi } from "@shared/hibikiApi";
import type { DailyActivity, LibraryEntry, RatingSyncResult, SourceAccount, SourceInfo, SourceLibraryEntry, WatchProgress } from "@shared/types";
import { library, watchProgress, dailyActivity, titleRatings } from "../db/schema";
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { deviceId } from "../sync/changes";
import { requestSync } from "../sync/client";
import { onEpisodeWatched, onLibraryChanged } from "../tracking/tracker";

const getDb = () => getPlatform().db.get();

/** What the library needs from the extension runtime: mirroring changes to a source's account. */
export interface LibrarySyncRuntime {
  list(): SourceInfo[];
  isLibrarySyncEnabled(sourceId: string): Promise<boolean>;
  getAccount(sourceId: string): Promise<SourceAccount | null>;
  listLibrary(sourceId: string): Promise<SourceLibraryEntry[]>;
  syncLibraryEntry(sourceId: string, request: { animeId: string; category: string | null; rating?: number | null }): Promise<void>;
}

// A day's "date" key is the local calendar day (not UTC), so activity attributes to the day the
// user actually watched it in their own timezone.
function localDateKey(epochMs: number): string {
  const d = new Date(epochMs);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

// Caps a single progress-save's contribution to "time watched today" - saves land every few
// seconds during real playback, so a legitimate delta is small; without a cap, seeking far
// forward would inflate watch-time stats by the size of the seek instead of time actually spent.
const MAX_WATCHED_DELTA_MS = 30_000;

/**
 * Mirrors one library change to the source's account, when that source syncs.
 *
 * Here rather than in the screens that change the library: there are already four callers and
 * every future one would have to remember. Deliberately not awaited and never allowed to throw -
 * a library change is local and must succeed whether or not a website is reachable, and a failed
 * push is a log line, not a refused edit. The next change to that title pushes the current state
 * anyway, so nothing needs a retry queue to stay eventually correct.
 */
function pushToAccount(
  runtime: LibrarySyncRuntime,
  sourceId: string,
  animeId: string,
  category: string | null,
): void {
  void runtime
    .isLibrarySyncEnabled(sourceId)
    .then(async (enabled) => {
      if (!enabled) return;
      await runtime.syncLibraryEntry(sourceId, { animeId, category });
      logger.info("account", `${sourceId}/${animeId} -> ${category ?? "removed"}`);
    })
    .catch((error: unknown) => {
      logger.warn("account", `${sourceId}/${animeId} not synced: ${error instanceof Error ? error.message : String(error)}`);
    });
}

/**
 * Mirrors a rating to the source's account, carrying the category the title already has there.
 *
 * The category matters: a source reads `syncLibraryEntry` as one statement about a title, and
 * YummyAnime removes it from its lists when that statement carries no category. So a rating sent
 * without one would quietly un-list a title the user only meant to score. The local library answers
 * that for anything in it, and for anything else the source's own list is asked once.
 *
 * Deliberately not gated on the library-sync switch, unlike the library pushes above. That switch
 * is about mirroring lists in the background, and this is someone pressing a number: an explicit
 * act deserves to reach the account it is obviously meant for. What it does need is the transport
 * (a source that syncs libraries at all) and an account to send it to - and it says which of those
 * was missing rather than failing silently, which is how a rating that never left this machine
 * looked exactly like one that did.
 */
async function pushRating(
  runtime: LibrarySyncRuntime,
  sourceId: string,
  animeId: string,
  rating: number,
): Promise<RatingSyncResult> {
  const source = runtime.list().find((candidate) => candidate.id === sourceId);
  if (!source?.capabilities.includes("LIBRARY_SYNC")) return { synced: false, reason: "unsupported" };
  const account = await runtime.getAccount(sourceId).catch(() => null);
  if (!account) return { synced: false, reason: "signed-out" };

  const local = await getDb()
    .select()
    .from(library)
    .where(and(eq(library.sourceId, sourceId), eq(library.animeId, animeId)))
    .get();
  let category = local?.category ?? null;
  if (!category) {
    category = (await runtime.listLibrary(sourceId)).find((entry) => entry.animeId === animeId)?.category ?? null;
  }
  await runtime.syncLibraryEntry(sourceId, { animeId, category, rating });
  logger.info("account", `${sourceId}/${animeId} rated ${rating}`);
  return { synced: true };
}

/** The ratings, library and watch-progress parts of `window.hibiki`. */
export function createLibraryApi(runtime: LibrarySyncRuntime): Pick<HibikiApi, "ratings" | "library" | "progress"> {
  return {
    ratings: {
      async get(sourceId: string, animeId: string): Promise<number | null> {
        const row = await getDb()
          .select()
          .from(titleRatings)
          .where(and(eq(titleRatings.sourceId, sourceId), eq(titleRatings.animeId, animeId)))
          .get();
        return row?.rating ?? null;
      },

      async set(sourceId: string, animeId: string, rating: number | null): Promise<RatingSyncResult> {
        const db = getDb();
        if (rating == null) {
          await db.delete(titleRatings).where(and(eq(titleRatings.sourceId, sourceId), eq(titleRatings.animeId, animeId))).run();
        } else {
          const values = { sourceId, animeId, rating, ratedAt: Date.now() };
          await db
            .insert(titleRatings)
            .values(values)
            .onConflictDoUpdate({ target: [titleRatings.sourceId, titleRatings.animeId], set: { rating, ratedAt: values.ratedAt } })
            .run();
        }
        requestSync();
        // Kept local first and pushed after: a rating is this app's own record, and a source that is
        // unreachable, signed out, or simply slow must not cost the user their answer. Awaited, unlike
        // the library push, so the screen can say whether the account got it.
        if (rating == null) return { synced: false, reason: "unsupported" };
        return pushRating(runtime, sourceId, animeId, rating).catch((error: unknown) => {
          logger.warn("account", `${sourceId}/${animeId} rating not synced: ${error instanceof Error ? error.message : String(error)}`);
          return { synced: false, reason: "failed" } as const;
        });
      },
    },

    library: {
      async list(): Promise<LibraryEntry[]> {
        const rows = await getDb().select().from(library).all();
        return rows.map((r) => ({
          animeId: r.animeId,
          sourceId: r.sourceId,
          category: r.category as LibraryEntry["category"],
          addedAt: r.addedAt,
          anime: JSON.parse(r.animeJson),
        }));
      },

      async upsert(entry: LibraryEntry): Promise<void> {
        // Read before the write: AniList hears only of a category that actually changed, not of every
        // refresh of a title's stored data (see tracking/tracker.ts).
        const previous = await getDb()
          .select({ category: library.category })
          .from(library)
          .where(and(eq(library.sourceId, entry.sourceId), eq(library.animeId, entry.animeId)))
          .get();
        await getDb()
          .insert(library)
          .values({
            animeId: entry.animeId,
            sourceId: entry.sourceId,
            category: entry.category,
            addedAt: entry.addedAt,
            animeJson: JSON.stringify(entry.anime),
          })
          .onConflictDoUpdate({
            target: [library.sourceId, library.animeId],
            set: { category: entry.category, animeJson: JSON.stringify(entry.anime) },
          })
          .run();
        pushToAccount(runtime, entry.sourceId, entry.animeId, entry.category);
        requestSync();
        onLibraryChanged({
          sourceId: entry.sourceId,
          animeId: entry.animeId,
          anime: entry.anime,
          previous: (previous?.category as LibraryEntry["category"] | undefined) ?? null,
          category: entry.category,
        });
      },

      async remove(sourceId: string, animeId: string): Promise<void> {
        await getDb()
          .delete(library)
          .where(and(eq(library.sourceId, sourceId), eq(library.animeId, animeId)))
          .run();
        pushToAccount(runtime, sourceId, animeId, null);
        requestSync();
      },
    },

    progress: {
      async get(sourceId: string, titleId: string, episodeId: string): Promise<WatchProgress | null> {
        const row = await getDb()
          .select()
          .from(watchProgress)
          .where(
            and(
              eq(watchProgress.sourceId, sourceId),
              eq(watchProgress.titleId, titleId),
              eq(watchProgress.episodeId, episodeId),
            ),
          )
          .get();
        return row ?? null;
      },

      // With better-sqlite3 every awaited statement below runs to completion before control returns,
      // so the read of `previous` and the writes that depend on it cannot interleave with another save.
      // An asynchronous driver loses that for free and needs a transaction here.
      async upsert(incoming: WatchProgress): Promise<void> {
        // Split off before the row is written: it describes this save, not the episode, and there is
        // no column for it.
        const { watchedDeltaMs: measuredWatchedMs, ...progress } = incoming;
        const db = getDb();
        const previous = await db
          .select()
          .from(watchProgress)
          .where(
            and(
              eq(watchProgress.sourceId, progress.sourceId),
              eq(watchProgress.titleId, progress.titleId),
              eq(watchProgress.episodeId, progress.episodeId),
            ),
          )
          .get();

        await db
          .insert(watchProgress)
          .values(progress)
          .onConflictDoUpdate({
            target: [watchProgress.sourceId, watchProgress.titleId, watchProgress.episodeId],
            set: {
              episodeNumber: progress.episodeNumber,
              groupId: progress.groupId,
              positionMs: progress.positionMs,
              durationMs: progress.durationMs,
              quality: progress.quality,
              translation: progress.translation,
              playerName: progress.playerName,
              // Sticky: once an episode is marked watched, a later rewind-and-stop shouldn't un-mark
              // it. Coerced to 0/1 — better-sqlite3 can't bind a raw JS boolean as a sql`` param (the
              // "watched" column itself round-trips fine through drizzle's own boolean mode, but a
              // value interpolated straight into a raw sql`` template bypasses that conversion).
              watched: sql`${watchProgress.watched} OR ${progress.watched ? 1 : 0}`,
              updatedAt: progress.updatedAt,
            },
          })
          .run();

        // Attribute this save to the local day it happened on, for the profile's activity stats.
        //
        // How far the position moved is only a stand-in for time watched, and a poor one: seeking
        // across a film books the whole jump as watched, capped at MAX_WATCHED_DELTA_MS per save but
        // still wrong every time. The player counts what actually played and sends it, so use that
        // whenever it is there; the fallback is for callers that only move the position.
        const watchedDeltaMs =
          measuredWatchedMs !== undefined
            ? Math.max(0, measuredWatchedMs)
            : Math.max(0, Math.min(progress.positionMs - (previous?.positionMs ?? 0), MAX_WATCHED_DELTA_MS));
        const newlyCompleted = progress.watched && !(previous?.watched ?? false);
        if (newlyCompleted) onEpisodeWatched(progress.sourceId, progress.titleId);
        if (watchedDeltaMs > 0 || newlyCompleted) {
          const date = localDateKey(progress.updatedAt);
          // This device's own row for the day: synced devices each add to theirs (see core/sync).
          await db
            .insert(dailyActivity)
            .values({ date, deviceId: await deviceId(), watchedMs: watchedDeltaMs, completedCount: newlyCompleted ? 1 : 0 })
            .onConflictDoUpdate({
              target: [dailyActivity.date, dailyActivity.deviceId],
              set: {
                watchedMs: sql`${dailyActivity.watchedMs} + ${watchedDeltaMs}`,
                completedCount: sql`${dailyActivity.completedCount} + ${newlyCompleted ? 1 : 0}`,
              },
            })
            .run();
        }
        requestSync();
      },

      async listRecent(limit: number) {
        return await getDb().select().from(watchProgress).orderBy(desc(watchProgress.updatedAt)).limit(limit).all();
      },

      async listForAnime(sourceId: string, titleId: string): Promise<WatchProgress[]> {
        return await getDb()
          .select()
          .from(watchProgress)
          .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, titleId)))
          .all();
      },

      async removeForAnime(sourceId: string, titleId: string): Promise<void> {
        await getDb()
          .delete(watchProgress)
          .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, titleId)))
          .run();
        requestSync();
      },

      // Same as removeForAnime but scoped to one episode - the history page (see
      // routes/history.tsx) shows one row per episode rather than per anime, so "delete this entry"
      // there means just this row, not every episode of the title.
      async removeEpisode(sourceId: string, titleId: string, episodeId: string): Promise<void> {
        await getDb()
          .delete(watchProgress)
          .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, titleId), eq(watchProgress.episodeId, episodeId)))
          .run();
        requestSync();
      },

      // Only ever updates an existing row (see VideoPlayer's onCaptureThumbnail, fired on pause/leave)
      // - a progress row for this episode should already exist by the time a real frame is worth
      // capturing; if one doesn't yet (playback just started), this is a harmless no-op update of 0
      // rows rather than inventing a progress row from just a thumbnail.
      async saveThumbnail(sourceId: string, titleId: string, episodeId: string, dataUrl: string): Promise<void> {
        await getDb()
          .update(watchProgress)
          .set({ thumbnailDataUrl: dataUrl })
          .where(and(eq(watchProgress.sourceId, sourceId), eq(watchProgress.titleId, titleId), eq(watchProgress.episodeId, episodeId)))
          .run();
      },

      async listDailyActivity(days: number): Promise<DailyActivity[]> {
        const since = localDateKey(Date.now() - (days - 1) * 86_400_000);
        // A day is the sum of every device's row for it.
        return await getDb()
          .select({
            date: dailyActivity.date,
            watchedMs: sql<number>`sum(${dailyActivity.watchedMs})`.mapWith(Number),
            completedCount: sql<number>`sum(${dailyActivity.completedCount})`.mapWith(Number),
          })
          .from(dailyActivity)
          .where(gte(dailyActivity.date, since))
          .groupBy(dailyActivity.date)
          .orderBy(dailyActivity.date)
          .all();
      },
    },
  };
}
