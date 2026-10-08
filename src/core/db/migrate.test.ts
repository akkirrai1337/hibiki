import { describe, expect, it } from "vitest";
import { applyMigrations, type MigrationConnection, type MigrationJournal } from "./migrate";

const journal: MigrationJournal = {
  entries: [
    { idx: 1, when: 200, tag: "0001_second", breakpoints: true },
    { idx: 0, when: 100, tag: "0000_first", breakpoints: true },
  ],
};
const files = {
  "0000_first": "CREATE TABLE a (x);\n--> statement-breakpoint\nCREATE TABLE b (y);",
  "0001_second": "ALTER TABLE a ADD z;",
};

function fakeConnection(lastAppliedAt: number | null, failOn?: string): MigrationConnection & { log: string[] } {
  const log: string[] = [];
  return {
    log,
    async exec(sql) {
      log.push(sql.trim());
      if (failOn && sql.includes(failOn)) throw new Error("boom");
    },
    async values() {
      return lastAppliedAt === null ? [] : [[1, "hash", String(lastAppliedAt)]];
    },
  };
}

const hash = async (text: string) => `h(${text.length})`;

describe("applyMigrations", () => {
  it("runs every statement of every migration in journal order, in one transaction", async () => {
    const connection = fakeConnection(null);
    expect(await applyMigrations(journal, files, connection, hash)).toBe(2);
    expect(connection.log.slice(1)).toEqual([
      "BEGIN",
      "CREATE TABLE a (x);",
      "CREATE TABLE b (y);",
      `INSERT INTO "__drizzle_migrations" ("hash", "created_at") VALUES('h(${files["0000_first"].length})', '100')`,
      "ALTER TABLE a ADD z;",
      `INSERT INTO "__drizzle_migrations" ("hash", "created_at") VALUES('h(${files["0001_second"].length})', '200')`,
      "COMMIT",
    ]);
  });

  it("skips what is already applied, as drizzle does, by the last created_at", async () => {
    const connection = fakeConnection(100);
    expect(await applyMigrations(journal, files, connection, hash)).toBe(1);
    expect(connection.log).toContain("ALTER TABLE a ADD z;");
    expect(connection.log).not.toContain("CREATE TABLE a (x);");
    expect(await applyMigrations(journal, files, fakeConnection(200), hash)).toBe(0);
  });

  it("rolls back and rethrows when a statement fails", async () => {
    const connection = fakeConnection(null, "CREATE TABLE b");
    await expect(applyMigrations(journal, files, connection, hash)).rejects.toThrow("boom");
    expect(connection.log.at(-1)).toBe("ROLLBACK");
    expect(connection.log).not.toContain("COMMIT");
  });
});
