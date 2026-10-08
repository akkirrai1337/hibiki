// DbPort on Android: drizzle's sqlite-proxy driver over the HibikiDb plugin, with the same schema and
// migrations as desktop (core/db). The connection opens lazily on the first query, so get() stays
// synchronous; close() lets a backup restore replace the file and the next query reopens it.
import { drizzle } from "drizzle-orm/sqlite-proxy";
import * as schema from "../../core/db/schema";
import { applyMigrations, type MigrationJournal } from "../../core/db/migrate";
import type { DbPort, HibikiDatabase } from "../types";
import journal from "../../core/db/migrations/meta/_journal.json";
import { HibikiDb } from "./native";

const migrationFiles = import.meta.glob<string>("../../core/db/migrations/*.sql", { query: "?raw", import: "default", eager: true });

function sqlByTag(): Record<string, string> {
  const byTag: Record<string, string> = {};
  for (const [path, text] of Object.entries(migrationFiles)) {
    byTag[path.split("/").pop()!.replace(/\.sql$/, "")] = text;
  }
  return byTag;
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function createAndroidDb(databasePath: string): DbPort & { migrate(): Promise<number> } {
  let opening: Promise<void> | null = null;
  const ensureOpen = () => (opening ??= HibikiDb.open({ path: databasePath }));

  const db = drizzle(
    async (sql, params, method) => {
      await ensureOpen();
      const { rows } = await HibikiDb.query({ sql, params, method });
      // sqlite-proxy's contract: "get" answers with the one row itself (undefined when there is
      // none), the others with the list of rows.
      if (method === "get") return { rows: (rows[0] ?? undefined) as unknown as unknown[] };
      return { rows };
    },
    { schema },
  ) as unknown as HibikiDatabase;

  return {
    get: () => db,
    async checkpoint() {
      await ensureOpen();
      await HibikiDb.query({ sql: "PRAGMA wal_checkpoint(TRUNCATE)", params: [], method: "all" });
    },
    async close() {
      if (!opening) return;
      await opening.catch(() => {});
      opening = null;
      await HibikiDb.close();
    },
    async migrate() {
      await ensureOpen();
      return applyMigrations(
        journal as MigrationJournal,
        sqlByTag(),
        {
          exec: (sql) => HibikiDb.exec({ sql }),
          values: async (sql) => (await HibikiDb.query({ sql, params: [], method: "values" })).rows,
        },
        sha256Hex,
      );
    },
  };
}
