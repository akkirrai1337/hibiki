import { checkpointDb, closeDb, getDb } from "../../main/db";
import type { DbPort } from "../types";

/** better-sqlite3, opened and migrated by main/db/index.ts. */
export const electronDb: DbPort = {
  get: () => getDb(),
  checkpoint: async () => checkpointDb(),
  close: async () => closeDb(),
};
