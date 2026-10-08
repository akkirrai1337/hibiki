import path from "node:path";
import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { app } from "electron";
import * as schema from "../../core/db/schema";

let dbInstance: ReturnType<typeof drizzle<typeof schema>> | null = null;
let sqliteInstance: Database.Database | null = null;

export const DB_PATH = path.join(app.getPath("userData"), "hibiki.db");

export function getDb() {
  if (dbInstance) return dbInstance;
  const sqlite = new Database(DB_PATH);
  sqlite.pragma("journal_mode = WAL");
  sqliteInstance = sqlite;
  dbInstance = drizzle(sqlite, { schema });
  const migrationsFolder = app.isPackaged
    ? path.join(process.resourcesPath, "migrations")
    : path.join(app.getAppPath(), "src/core/db/migrations");
  migrate(dbInstance, { migrationsFolder });
  return dbInstance;
}

// Backs up/restores (see main/backup.ts) both need the on-disk .db file to be a single,
// self-contained snapshot - WAL journal mode (see above) normally leaves recent writes sitting in
// a separate `-wal` file instead, which a plain copy of just `hibiki.db` would silently miss.
// TRUNCATE folds everything back into the main file and empties the WAL, rather than the default
// PASSIVE checkpoint, which only does that opportunistically when nothing else has the WAL open.
export function checkpointDb(): void {
  sqliteInstance?.pragma("wal_checkpoint(TRUNCATE)");
}

// Restoring a backup overwrites hibiki.db out from under this open handle - closing it first
// (rather than leaving better-sqlite3 to find out the hard way on its next query) means the next
// getDb() call after the app relaunches opens the freshly-restored file cleanly.
export function closeDb(): void {
  sqliteInstance?.close();
  sqliteInstance = null;
  dbInstance = null;
}
