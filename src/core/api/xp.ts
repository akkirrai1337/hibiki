import { desc } from "drizzle-orm";
import type { HibikiApi } from "@shared/hibikiApi";
import type { XpEvent } from "@shared/types";
import { xpEvents } from "../db/schema";
import { getPlatform } from "../platform";
import { requestSync } from "../sync/client";

const getDb = () => getPlatform().db.get();

// Recent-first, capped - a running history is meant to be skimmed, not paginated through.
const HISTORY_LIMIT = 100;

/** The xp-history part of `window.hibiki`. */
export function createXpApi(): HibikiApi["xp"] {
  return {
    async list(): Promise<XpEvent[]> {
      return await getDb().select().from(xpEvents).orderBy(desc(xpEvents.id)).limit(HISTORY_LIMIT).all();
    },

    async record(kind: string, xp: number, createdAt: number): Promise<void> {
      await getDb().insert(xpEvents).values({ kind, xp, createdAt }).run();
      requestSync();
    },

    // Only ever clears this log - achievement/level progress is derived from watch time and
    // unlocked tiers (see levelProgress.ts), not from these rows, so this can't accidentally take
    // XP or levels away, just the record of how they were earned.
    async clear(): Promise<void> {
      await getDb().delete(xpEvents).run();
      requestSync();
    },
  };
}
