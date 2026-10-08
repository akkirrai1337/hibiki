import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import { describe, expect, it, vi } from "vitest";
import * as schema from "../db/schema";
import { library, watchProgress } from "../db/schema";
import { applyMigrations, type MigrationJournal } from "../db/migrate";
import { DEFAULT_REPOSITORY_URL } from "../marketplace";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import { findMissingSources, sourcesInData } from "./sourceAutoInstall";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const entry = (id: string) => ({ id, name: id, version: "1.0.0", lang: "ru", capabilities: [], resolverDependencies: [], isNsfw: false, type: "source", manifestUrl: `https://x/${id}.manifest.json` });

describe("sources the synced data needs", () => {
  it("lists the uninstalled ones a repository has, and counts the rest", async () => {
    const db = await openDb();
    installPlatform({
      db: { get: () => db },
      paths: { userData: "data" },
      files: { join: (...parts: string[]) => parts.join("/"), exists: async () => true, writeText: async () => undefined },
      http: { request: async ({ url }: { url: string }) => ({ status: 200, url, headers: {}, body: JSON.stringify({ extensions: [entry("yummy-anime"), entry("installed")] }) }) },
    } as unknown as Platform);
    await db.insert(schema.sourceRepositories).values({ url: DEFAULT_REPOSITORY_URL, addedAt: 1 }).run();
    await db.insert(library).values({ sourceId: "installed", animeId: "a", category: "watching", addedAt: 1, animeJson: "{}" }).run();
    await db.insert(watchProgress).values({ sourceId: "yummy-anime", titleId: "4689", episodeId: "e", episodeNumber: 1, positionMs: 1, durationMs: 2, updatedAt: 1 }).run();
    await db.insert(watchProgress).values({ sourceId: "gone", titleId: "1", episodeId: "e", episodeNumber: 1, positionMs: 1, durationMs: 2, updatedAt: 1 }).run();
    await db.insert(watchProgress).values({ sourceId: "apk:42", titleId: "1", episodeId: "e", episodeNumber: 1, positionMs: 1, durationMs: 2, updatedAt: 1 }).run();

    expect((await sourcesInData()).sort()).toEqual(["apk:42", "gone", "installed", "yummy-anime"]);
    const runtime = { list: () => [{ id: "installed" }] } as unknown as ExtensionRuntime;
    const missing = await findMissingSources(runtime, await sourcesInData(), { apkFromDefaultOnly: false });
    // No APK sources on this platform: apk:42 is neither offered nor counted.
    expect(missing.available.map((item) => item.extension.id)).toEqual(["yummy-anime"]);
    expect(missing.available[0].repositoryUrl).toBe(DEFAULT_REPOSITORY_URL);
    expect(missing.unavailable).toEqual(["gone"]);
  });
});

async function openDb() {
  const migrationsDir = fileURLToPath(new URL("../db/migrations", import.meta.url));
  const journal = JSON.parse(readFileSync(`${migrationsDir}/meta/_journal.json`, "utf-8")) as MigrationJournal;
  const sqlByTag = Object.fromEntries(
    readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).map((name) => [name.replace(/\.sql$/, ""), readFileSync(`${migrationsDir}/${name}`, "utf-8")]),
  );
  const sqlite = new DatabaseSync(":memory:");
  const values = (sql: string, params: unknown[]) => sqlite.prepare(sql).all(...(params as SQLInputValue[])).map((row) => Object.values(row));
  const db = drizzle(async (sql, params, method) => {
    if (method === "run") {
      sqlite.prepare(sql).run(...(params as SQLInputValue[]));
      return { rows: [] };
    }
    const rows = values(sql, params);
    return { rows: method === "get" ? (rows[0] as unknown as unknown[]) : rows };
  }, { schema });
  await applyMigrations(journal, sqlByTag, {
    exec: async (sql) => void sqlite.exec(sql),
    values: async (sql) => values(sql, []),
  }, async (text) => createHash("sha256").update(text).digest("hex"));
  return db;
}
