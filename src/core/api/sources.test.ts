// What the title page's two-part load leaves in the title cache (core/titleParts.ts), over a real
// migrated database (node:sqlite, as in downloads.test.ts): recommendations read a watched title's
// similar list from there, so the halves must end up together.
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import { describe, expect, it, vi } from "vitest";
import * as schema from "../db/schema";
import { applyMigrations, type MigrationJournal } from "../db/migrate";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import type { AnimeTitle, RelatedAnimeTitle, TitleExtras } from "@shared/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import { getCachedAnime } from "../offlineCache";
import { createSourcesApi } from "./sources";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const migrationsDir = fileURLToPath(new URL("../db/migrations", import.meta.url));
const journal = JSON.parse(readFileSync(`${migrationsDir}/meta/_journal.json`, "utf-8")) as MigrationJournal;
const sqlByTag = Object.fromEntries(
  readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).map((name) => [name.replace(/\.sql$/, ""), readFileSync(`${migrationsDir}/${name}`, "utf-8")]),
);

const similar = (...ids: string[]): RelatedAnimeTitle[] => ids.map((id) => ({ id, title: `Title ${id}` }));

async function setup(runtimeParts: Partial<Record<"getById" | "getTitleCore" | "getTitleExtras", unknown>>) {
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
  await applyMigrations(journal, sqlByTag, { exec: async (sql) => void sqlite.exec(sql), values: async (sql) => values(sql, []) }, async (text) => createHash("sha256").update(text).digest("hex"));
  installPlatform({ db: { get: () => db } } as unknown as Platform);
  return createSourcesApi(runtimeParts as unknown as ExtensionRuntime);
}

const pendingTitle = (name = "One"): AnimeTitle => ({ id: "1", sourceId: "src", russianName: name, similarAnime: [], extrasPending: true });

describe("the title page's two-part load", () => {
  it("caches a first visit's title marked as waiting for its extras", async () => {
    const getTitleExtras = vi.fn();
    const api = await setup({ getTitleCore: async () => pendingTitle(), getTitleExtras });
    const title = await api.getById("src", "1", { extras: false });
    expect(title.extrasPending).toBe(true);
    expect(getTitleExtras).not.toHaveBeenCalled();
    expect((await getCachedAnime("src", "1"))?.extrasPending).toBe(true);
  });

  it("completes the cached title when the extras arrive", async () => {
    const extras: TitleExtras = { similarAnime: similar("a", "b") };
    const api = await setup({ getTitleCore: async () => pendingTitle(), getTitleExtras: async () => extras });
    await api.getById("src", "1", { extras: false });
    expect(await api.titleExtras("src", "1")).toEqual(extras);
    const cached = await getCachedAnime("src", "1");
    expect(cached).not.toHaveProperty("extrasPending");
    expect(cached?.similarAnime?.map((entry) => entry.id)).toEqual(["a", "b"]);
    expect(cached?.russianName).toBe("One");
  });

  it("on a revisit shows the similar titles found before, keeps them cached, and still asks for fresh ones", async () => {
    const api = await setup({ getTitleCore: async () => pendingTitle("One (renamed)"), getTitleExtras: async () => ({ similarAnime: similar("a") }) });
    await api.getById("src", "1", { extras: false });
    await api.titleExtras("src", "1");
    const again = await api.getById("src", "1", { extras: false });
    expect(again).toMatchObject({ russianName: "One (renamed)", extrasPending: true });
    expect(again.similarAnime?.map((entry) => entry.id)).toEqual(["a"]);
    const cached = await getCachedAnime("src", "1");
    expect(cached).not.toHaveProperty("extrasPending");
    expect(cached?.similarAnime?.map((entry) => entry.id)).toEqual(["a"]);
  });

  it("treats a source that answers in full as it always did", async () => {
    const whole: AnimeTitle = { id: "1", sourceId: "src", russianName: "One", similarAnime: similar("z") };
    const api = await setup({ getTitleCore: async () => whole, getById: async () => whole });
    const title = await api.getById("src", "1", { extras: false });
    expect(title).toEqual(whole);
    expect((await getCachedAnime("src", "1"))?.similarAnime?.map((entry) => entry.id)).toEqual(["z"]);
  });

  it("returns everything from a plain getById", async () => {
    const getTitleCore = vi.fn();
    const whole: AnimeTitle = { id: "1", sourceId: "src", russianName: "One", similarAnime: similar("z") };
    const api = await setup({ getTitleCore, getById: async () => whole });
    expect(await api.getById("src", "1")).toEqual(whole);
    expect(getTitleCore).not.toHaveBeenCalled();
  });

  it("has nothing to cache or return when the source adds nothing", async () => {
    const api = await setup({ getTitleCore: async () => pendingTitle(), getTitleExtras: async () => null });
    await api.getById("src", "1", { extras: false });
    expect(await api.titleExtras("src", "1")).toBeNull();
    expect((await getCachedAnime("src", "1"))?.extrasPending).toBe(true);
  });
});
