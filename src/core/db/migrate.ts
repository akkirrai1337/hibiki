// Applies the drizzle-kit migrations in core/db/migrations the way drizzle-orm's own migrator does -
// same `__drizzle_migrations` table, same "newer than the last applied one" rule, same statement
// breakpoints - for drivers whose host cannot read the migrations folder itself. Desktop keeps using
// drizzle's better-sqlite3 migrator (main/db/index.ts); Android bundles the files and comes here.

export interface MigrationJournal {
  entries: Array<{ idx: number; when: number; tag: string; breakpoints: boolean }>;
}

export interface MigrationConnection {
  /** Runs one statement that returns no rows. */
  exec(sql: string): Promise<void>;
  /** Runs a query and returns its rows as value arrays. */
  values(sql: string): Promise<unknown[][]>;
}

const TABLE = "__drizzle_migrations";

/** `sqlByTag` maps each journal tag to the text of `<tag>.sql`; `hash` is SHA-256 hex, as drizzle stores. */
export async function applyMigrations(
  journal: MigrationJournal,
  sqlByTag: Record<string, string>,
  connection: MigrationConnection,
  hash: (text: string) => Promise<string>,
): Promise<number> {
  await connection.exec(`CREATE TABLE IF NOT EXISTS "${TABLE}" (id SERIAL PRIMARY KEY, hash text NOT NULL, created_at numeric)`);
  const [last] = await connection.values(`SELECT id, hash, created_at FROM "${TABLE}" ORDER BY created_at DESC LIMIT 1`);
  const lastAppliedAt = last ? Number(last[2]) : null;

  const pending = [...journal.entries]
    .sort((a, b) => a.idx - b.idx)
    .filter((entry) => lastAppliedAt === null || lastAppliedAt < entry.when);
  if (pending.length === 0) return 0;

  await connection.exec("BEGIN");
  try {
    for (const entry of pending) {
      const text = sqlByTag[entry.tag];
      if (text === undefined) throw new Error(`Migration ${entry.tag} is missing from the bundle`);
      for (const statement of text.split("--> statement-breakpoint")) {
        if (statement.trim()) await connection.exec(statement);
      }
      await connection.exec(`INSERT INTO "${TABLE}" ("hash", "created_at") VALUES('${await hash(text)}', '${entry.when}')`);
    }
    await connection.exec("COMMIT");
  } catch (error) {
    await connection.exec("ROLLBACK").catch(() => {});
    throw error;
  }
  return pending.length;
}
