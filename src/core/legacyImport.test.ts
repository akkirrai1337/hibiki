import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import { describe, expect, it, vi } from "vitest";
import * as schema from "./db/schema";
import { library, watchProgress } from "./db/schema";
import { applyMigrations, type MigrationJournal } from "./db/migrate";
import { installPlatform } from "./platform";
import type { Platform } from "../platform/types";
import { importLegacyData, legacyAnimeTitle, legacyCategory, legacyProgressRows, legacyTitleKey } from "./legacyImport";

vi.mock("./logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const SEP = "\u001f";
const chainsawJson = JSON.stringify({ id: "source:ani-liberty:9712", title: "Человек-бензопила", posterUrl: "https://p/1.jpg", alternativeTitles: ["Chainsaw Man"], genres: ["Экшен"] });

describe("old title ids", () => {
  it("map onto this app's sources", () => {
    expect(legacyTitleKey("source:ani-liberty:9712")).toEqual({ sourceId: "ani-liberty", animeId: "9712" });
    expect(legacyTitleKey("source:ANI_LIBERTY:42")).toEqual({ sourceId: "ani-liberty", animeId: "42" });
    expect(legacyTitleKey("4321")).toEqual({ sourceId: "yummy-anime", animeId: "4321" });
    expect(legacyTitleKey("source:animego:path/with:colon")).toEqual({ sourceId: "animego", animeId: "path/with:colon" });
    expect(legacyTitleKey("nonsense")).toBeNull();
  });

  it("turn an Aniyomi source's base-36 id into the decimal one APK sources have here", () => {
    // Long.toUnsignedString(id, 36) there, Long.toUnsignedString(id) here.
    const id = 7_652_394_481_221_133_210n;
    expect(legacyTitleKey(`source:aniyomi-allanime-1a2b3c-${id.toString(36)}:/anime/x`)).toEqual({ sourceId: `apk:${id}`, animeId: "/anime/x" });
  });
});

describe("old library rows", () => {
  it("keep the status before favourite and saved", () => {
    expect(legacyCategory("saved,favorite,watching")).toBe("watching");
    expect(legacyCategory("favorite,saved")).toBe("favorite");
    expect(legacyCategory("")).toBeNull();
  });

  it("become a title a library card can draw", () => {
    expect(legacyAnimeTitle(chainsawJson, { sourceId: "ani-liberty", animeId: "9712" })).toMatchObject({
      id: "9712", sourceId: "ani-liberty", russianName: "Человек-бензопила", posterUrl: "https://p/1.jpg", synonyms: ["Chainsaw Man"],
    });
    expect(legacyAnimeTitle(null, { sourceId: "a", animeId: "b" })).toBeNull();
  });
});

describe("old saved positions", () => {
  it("keep the latest per episode and read every key layout", () => {
    const rows = legacyProgressRows({
      [`progress_source:ani-liberty:9712|episode|e1|source|9712|watch|anilibria-0`]: ["1", "x", "x", "AniLibria", "1080p", "1400000", "1440000", "200"].join(SEP),
      [`progress_source:ani-liberty:9712|episode|e1|source|9712|watch|other-1`]: ["1", "x", "x", "Other", "", "100", "1440000", "100"].join(SEP),
      [`progress_source:ani-liberty:9712:e2`]: ["2", "x", "x", "AniLibria", "", "60000", "1440000", "300"].join(SEP),
      "selected_source_9712": "ignored",
    });
    expect(rows).toHaveLength(2);
    expect(rows.find((row) => row.episodeId === "e1")).toMatchObject({ translation: "AniLibria", quality: "1080p", watched: true, updatedAt: 200 });
    expect(rows.find((row) => row.episodeId === "e2")).toMatchObject({ episodeNumber: 2, positionMs: 60000, watched: false });
  });
});

describe("importLegacyData", () => {
  it("writes the library and positions without touching what is already there", async () => {
    const db = await openDb();
    installPlatform({ db: { get: () => db } } as unknown as Platform);
    await db.insert(library).values({ sourceId: "yummy-anime", animeId: "1", category: "completed", addedAt: 1, animeJson: "{}" }).run();
    const result = await importLegacyData({
      library: [
        { titleId: "source:ani-liberty:9712", animeJson: chainsawJson, categories: "watching,favorite", addedAt: 50 },
        { titleId: "1", animeJson: JSON.stringify({ title: "Old" }), categories: "planned", addedAt: 2 },
        { titleId: "broken", animeJson: null, categories: "saved" },
      ],
      progress: { [`progress_source:ani-liberty:9712|episode|e1`]: ["1", "x", "x", "AniLibria", "", "5000", "1440000", "70"].join(SEP) },
    });
    expect(result).toMatchObject({ library: 2, progress: 1, skipped: 1 });
    expect(result.sourceIds.sort()).toEqual(["ani-liberty", "yummy-anime"]);
    const rows = await db.select().from(library).all();
    expect(rows.find((row) => row.animeId === "1")?.category).toBe("completed");
    expect(rows.find((row) => row.animeId === "9712")).toMatchObject({ category: "watching", addedAt: 50 });
    expect(await db.select().from(watchProgress).all()).toHaveLength(1);
  });
});

async function openDb() {
  const migrationsDir = fileURLToPath(new URL("./db/migrations", import.meta.url));
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
