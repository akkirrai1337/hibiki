// Two real databases, migrated as the Android build migrates them (core/db/migrate.ts over drizzle's
// async sqlite-proxy driver), standing in for two devices. node:sqlite rather than better-sqlite3:
// the latter is built for Electron and does not load in plain Node.
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { and, eq } from "drizzle-orm";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as schema from "../db/schema";
import { cachedAnime, dailyActivity, library, titleRatings, watchProgress, xpEvents } from "../db/schema";
import { applyMigrations, type MigrationJournal } from "../db/migrate";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import { applyChanges, collectChanges, currentSeq, deviceId, mergeProgress, type ProgressRow } from "./changes";
import { discover, forgetPeer, pair, syncNow } from "./client";
import { listPeers, removePeer } from "./peers";
import { handleSyncRequest, startPairing, stopPairing } from "./server";

const migrationsDir = fileURLToPath(new URL("../db/migrations", import.meta.url));
const journal = JSON.parse(readFileSync(`${migrationsDir}/meta/_journal.json`, "utf-8")) as MigrationJournal;
const sqlByTag = Object.fromEntries(
  readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).map((name) => [name.replace(/\.sql$/, ""), readFileSync(`${migrationsDir}/${name}`, "utf-8")]),
);

async function openDevice() {
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
type Db = Awaited<ReturnType<typeof openDevice>>;

let pc: Db;
let phone: Db;
function on(db: Db): Db {
  installPlatform({ db: { get: () => db } } as unknown as Platform);
  return db;
}

/** Everything one device has changed since `since`, applied on the other, as a sync round does. */
async function push(from: Db, to: Db, since = 0): Promise<number> {
  let cursor = since;
  for (;;) {
    on(from);
    const batch = await collectChanges(cursor);
    on(to);
    await applyChanges(batch);
    cursor = batch.upTo;
    if (!batch.more) return cursor;
  }
}

const progress = (overrides: Partial<ProgressRow> = {}): ProgressRow => ({
  sourceId: "s", titleId: "t", episodeId: "e1", episodeNumber: 1, groupId: "g", quality: null, translation: null, playerName: null,
  positionMs: 1000, durationMs: 1_400_000, watched: false, updatedAt: 1000, thumbnailDataUrl: null, ...overrides,
});

beforeEach(async () => {
  pc = await openDevice();
  phone = await openDevice();
});

describe("change tracking", () => {
  it("gives each database its own device id and numbers every write", async () => {
    on(pc);
    const pcId = await deviceId();
    const before = await currentSeq();
    await pc.insert(library).values({ sourceId: "s", animeId: "a", category: "watching", addedAt: 1, animeJson: "{}" }).run();
    await pc.update(library).set({ category: "completed" }).where(eq(library.animeId, "a")).run();
    expect(await currentSeq()).toBe(before + 2);
    const row = await pc.select().from(library).get();
    expect(row!.changeSeq).toBe(before + 2);
    // The category change dated itself.
    expect(row!.updatedAt).toBeGreaterThan(1_600_000_000_000);
    on(phone);
    expect(await deviceId()).not.toBe(pcId);
  });

  it("does not move the change time when only the stored title data is refreshed", async () => {
    on(pc);
    await pc.insert(library).values({ sourceId: "s", animeId: "a", category: "watching", addedAt: 1, animeJson: "{}" }).run();
    const first = (await pc.select().from(library).get())!.updatedAt;
    await pc.update(library).set({ animeJson: '{"x":1}' }).where(eq(library.animeId, "a")).run();
    expect((await pc.select().from(library).get())!.updatedAt).toBe(first);
  });
});

describe("syncing two devices", () => {
  it("brings the library, progress, ratings and xp across", async () => {
    on(phone);
    await phone.insert(library).values({ sourceId: "s", animeId: "a", category: "watching", addedAt: 5, animeJson: "{}" }).run();
    await phone.insert(watchProgress).values(progress()).run();
    await phone.insert(titleRatings).values({ sourceId: "s", animeId: "a", rating: 8, ratedAt: 10 }).run();
    await phone.insert(xpEvents).values({ kind: "collector_10", xp: 50, createdAt: 20 }).run();

    await push(phone, pc);
    on(pc);
    expect((await pc.select().from(library).get())?.category).toBe("watching");
    expect((await pc.select().from(watchProgress).get())?.positionMs).toBe(1000);
    expect((await pc.select().from(titleRatings).get())?.rating).toBe(8);
    expect(await pc.select().from(xpEvents).all()).toHaveLength(1);

    // Again: nothing doubles.
    await push(phone, pc);
    on(pc);
    expect(await pc.select().from(xpEvents).all()).toHaveLength(1);
  });

  it("brings the cached card of each synced title along", async () => {
    on(pc);
    await pc.insert(cachedAnime).values({ sourceId: "yummy-anime", animeId: "4689", animeJson: JSON.stringify({ id: "4689", russianName: "Бочки" }), cachedAt: 50 }).run();
    await pc.insert(watchProgress).values(progress({ sourceId: "yummy-anime", titleId: "4689" })).run();
    await push(pc, phone);
    on(phone);
    expect(JSON.parse((await phone.select().from(cachedAnime).get())!.animeJson).russianName).toBe("Бочки");

    // A fresher card here is not overwritten by an older one from there.
    await phone.update(cachedAnime).set({ animeJson: "{\"russianName\":\"newer\"}", cachedAt: 90 }).run();
    on(pc);
    await pc.update(watchProgress).set({ positionMs: 2000, updatedAt: 2000 }).run();
    await push(pc, phone, 0);
    on(phone);
    expect((await phone.select().from(cachedAnime).get())!.animeJson).toContain("newer");
  });

  it("keeps the later category change, whichever device made it", async () => {
    on(phone);
    await phone.insert(library).values({ sourceId: "s", animeId: "a", category: "watching", addedAt: 5, animeJson: "{}" }).run();
    await push(phone, pc);
    on(pc);
    await pc.update(library).set({ category: "completed", updatedAt: Date.now() + 10_000 }).where(eq(library.animeId, "a")).run();
    on(phone);
    await phone.update(library).set({ category: "dropped", updatedAt: Date.now() }).where(eq(library.animeId, "a")).run();
    await push(phone, pc);
    await push(pc, phone);
    on(pc);
    expect((await pc.select().from(library).get())?.category).toBe("completed");
    on(phone);
    expect((await phone.select().from(library).get())?.category).toBe("completed");
  });

  it("carries a deletion across, and lets a later re-add win over it", async () => {
    on(phone);
    await phone.insert(library).values({ sourceId: "s", animeId: "a", category: "watching", addedAt: 5, animeJson: "{}" }).run();
    await push(phone, pc);
    await phone.delete(library).where(eq(library.animeId, "a")).run();
    await push(phone, pc);
    on(pc);
    expect(await pc.select().from(library).all()).toHaveLength(0);

    // Added back on the PC afterwards: it stays, and goes back to the phone.
    await pc.insert(library).values({ sourceId: "s", animeId: "a", category: "planned", addedAt: 9, animeJson: "{}", updatedAt: Date.now() + 5_000 }).run();
    await push(pc, phone);
    on(phone);
    expect((await phone.select().from(library).get())?.category).toBe("planned");
  });

  it("adds up each device's watch time for a day instead of overwriting it", async () => {
    on(pc);
    const pcId = await deviceId();
    await pc.insert(dailyActivity).values({ date: "2026-10-07", deviceId: pcId, watchedMs: 60_000, completedCount: 1 }).run();
    on(phone);
    const phoneId = await deviceId();
    await phone.insert(dailyActivity).values({ date: "2026-10-07", deviceId: phoneId, watchedMs: 30_000, completedCount: 2 }).run();
    await push(phone, pc);
    await push(pc, phone);
    for (const db of [pc, phone]) {
      on(db);
      const rows = await db.select().from(dailyActivity).where(eq(dailyActivity.date, "2026-10-07")).all();
      expect(rows.reduce((sum, row) => sum + row.watchedMs, 0)).toBe(90_000);
      expect(rows.reduce((sum, row) => sum + row.completedCount, 0)).toBe(3);
    }
  });

  it("splits a large first sync into batches without losing rows", async () => {
    on(phone);
    for (let i = 0; i < 250; i++) {
      await phone.insert(watchProgress).values(progress({ episodeId: `e${i}`, episodeNumber: i })).run();
    }
    on(phone);
    const first = await collectChanges(0);
    expect(first.more).toBe(true);
    await push(phone, pc);
    on(pc);
    expect(await pc.select().from(watchProgress).all()).toHaveLength(250);
  });

  it("never un-watches an episode, and keeps the later position", async () => {
    on(pc);
    await pc.insert(watchProgress).values(progress({ watched: true, positionMs: 1_390_000, updatedAt: 5000 })).run();
    on(phone);
    await phone.insert(watchProgress).values(progress({ watched: false, positionMs: 200_000, updatedAt: 9000 })).run();
    await push(phone, pc);
    on(pc);
    const row = await pc.select().from(watchProgress).where(and(eq(watchProgress.episodeId, "e1"))).get();
    expect(row?.watched).toBe(true);
    expect(row?.positionMs).toBe(200_000);
  });
});

describe("mergeProgress", () => {
  it("keeps a frame from either side and reports no change when there is none", () => {
    const local = progress({ updatedAt: 10, thumbnailDataUrl: "data:local" });
    expect(mergeProgress(local, progress({ updatedAt: 20 }))?.thumbnailDataUrl).toBe("data:local");
    expect(mergeProgress(local, { ...local })).toBeNull();
  });
});

describe("migration of an existing database", () => {
  it("numbers what was already there, so a newly paired device receives all of it", async () => {
    const sqlite = new DatabaseSync(":memory:");
    const connection = {
      exec: async (sql: string) => void sqlite.exec(sql),
      values: async (sql: string) => sqlite.prepare(sql).all().map((row) => Object.values(row)),
    };
    const hash = async (text: string) => createHash("sha256").update(text).digest("hex");
    const syncIdx = journal.entries.find((entry) => entry.tag.startsWith("0018"))!.idx;
    const before = { entries: journal.entries.filter((entry) => entry.idx < syncIdx) };
    await applyMigrations(before, sqlByTag, connection, hash);
    sqlite.exec(`INSERT INTO library (source_id, anime_id, category, added_at, anime_json) VALUES ('s', 'a', 'watching', 111, '{}'), ('s', 'b', 'planned', 222, '{}')`);
    sqlite.exec(`INSERT INTO daily_activity (date, watched_ms, completed_count) VALUES ('2026-10-01', 5000, 1)`);
    sqlite.exec(`INSERT INTO xp_events (kind, xp, created_at) VALUES ('first', 10, 1)`);
    await applyMigrations(journal, sqlByTag, connection, hash);

    const library = sqlite.prepare("SELECT anime_id, updated_at, change_seq FROM library ORDER BY anime_id").all();
    expect(library.map((row) => row.updated_at)).toEqual([111, 222]);
    expect(library.every((row) => Number(row.change_seq) > 0)).toBe(true);
    const device = sqlite.prepare("SELECT value FROM sync_state WHERE key = 'device_id'").get()!.value;
    expect(sqlite.prepare("SELECT device_id FROM daily_activity").get()!.device_id).toBe(device);
    expect(sqlite.prepare("SELECT uid FROM xp_events").get()!.uid).toMatch(/^[0-9a-f]{32}$/);
    const seq = Number(sqlite.prepare("SELECT value FROM sync_state WHERE key = 'seq'").get()!.value);
    expect(seq).toBeGreaterThanOrEqual(2);
  });
});

describe("pairing and syncing over the protocol", () => {
  // The computer's request handler behind a fake network: each request runs with the computer's
  // database installed, then the phone's comes back.
  function connect(computer: Db, phoneDb: Db) {
    const events: string[] = [];
    const base = {
      secureStore: { isAvailable: async () => true, encrypt: async (text: string) => `x${text}`, decrypt: async (text: string) => text.slice(1) },
      events: { emit: (_channel: string, payload: unknown) => events.push((payload as { what: string }).what) },
    };
    const asComputer = () => installPlatform({ ...base, db: { get: () => computer } } as unknown as Platform);
    const asPhone = () => installPlatform({
      ...base,
      db: { get: () => phoneDb },
      syncTransport: {
        deviceName: async () => "Test phone",
        discover: async () => {
          asComputer();
          const id = await deviceId();
          asPhone();
          return [{ deviceId: id, name: "Test PC", host: "10.0.0.2", port: 47652 }];
        },
        request: async (_host: string, _port: number, message: string) => {
          asComputer();
          try {
            return await handleSyncRequest(message, "10.0.0.5", "Test PC");
          } finally {
            asPhone();
          }
        },
      },
    } as unknown as Platform);
    return { asComputer, asPhone, events };
  }

  it("refuses a wrong code, pairs with the right one, then syncs both ways", async () => {
    const net = connect(pc, phone);
    net.asComputer();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    const wrong = code === "000000" ? "111111" : "000000";
    await expect(pair(found, wrong)).rejects.toMatchObject({ code: "bad-code" });
    const device = await pair(found, code);
    expect(device.name).toBe("Test PC");

    await phone.insert(library).values({ sourceId: "s", animeId: "from-phone", category: "watching", addedAt: 1, animeJson: "{}" }).run();
    net.asComputer();
    await pc.insert(library).values({ sourceId: "s", animeId: "from-pc", category: "planned", addedAt: 2, animeJson: "{}" }).run();
    net.asPhone();
    await syncNow();

    const ids = async (db: Db) => (await db.select().from(library).all()).map((row) => row.animeId).sort();
    expect(await ids(phone)).toEqual(["from-pc", "from-phone"]);
    net.asComputer();
    expect(await ids(pc)).toEqual(["from-pc", "from-phone"]);
    // The computer lists the phone by the name it gave.
    expect((await listPeers()).map((peer) => peer.name)).toEqual(["Test phone"]);
  });

  /** Each device with its own title and an xp event, then paired in `mode` and synced. */
  async function pairWithData(mode: "keep-here" | "take-there") {
    const net = connect(pc, phone);
    await phone.insert(library).values({ sourceId: "s", animeId: "from-phone", category: "watching", addedAt: 1, animeJson: "{}" }).run();
    await phone.insert(xpEvents).values({ kind: "phone", xp: 5, createdAt: 1 }).run();
    net.asComputer();
    await pc.insert(library).values({ sourceId: "s", animeId: "from-pc", category: "planned", addedAt: 2, animeJson: "{}" }).run();
    await pc.insert(watchProgress).values(progress({ titleId: "from-pc" })).run();
    await pc.insert(xpEvents).values({ kind: "pc", xp: 7, createdAt: 2 }).run();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    await pair(found, code, mode);
    await syncNow();
    const state = async (db: Db) => ({
      library: (await db.select().from(library).all()).map((row) => row.animeId).sort(),
      progress: (await db.select().from(watchProgress).all()).map((row) => row.titleId),
      xp: (await db.select().from(xpEvents).all()).map((row) => row.kind).sort(),
    });
    return { net, state };
  }

  it("replaces the other device's data with this one's when asked to keep this one's", async () => {
    const { net, state } = await pairWithData("keep-here");
    const expected = { library: ["from-phone"], progress: [], xp: ["phone"] };
    expect(await state(phone)).toEqual(expected);
    net.asComputer();
    expect(await state(pc)).toEqual(expected);

    // Done once: what either adds afterwards is an ordinary merge.
    await pc.insert(library).values({ sourceId: "s", animeId: "later", category: "planned", addedAt: 3, animeJson: "{}" }).run();
    net.asPhone();
    await syncNow();
    expect((await state(phone)).library).toEqual(["from-phone", "later"]);
  });

  it("replaces this device's data with the other one's when asked to take it", async () => {
    const { net, state } = await pairWithData("take-there");
    const expected = { library: ["from-pc"], progress: ["from-pc"], xp: ["pc"] };
    expect(await state(phone)).toEqual(expected);
    net.asComputer();
    expect(await state(pc)).toEqual(expected);
  });

  async function paired() {
    const net = connect(pc, phone);
    net.asComputer();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    await pair(found, code);
    return { net, computerId: found.deviceId };
  }

  it("forgetting on the phone ends the pairing on the computer too", async () => {
    const { net, computerId } = await paired();
    await forgetPeer(computerId);
    expect(await listPeers()).toEqual([]);
    // The computer is told in the background.
    await vi.waitFor(async () => {
      net.asComputer();
      const left = await listPeers();
      net.asPhone();
      expect(left).toEqual([]);
    });
  });

  it("a device the other one forgot while it was away forgets it on its next sync", async () => {
    const { net } = await paired();
    net.asComputer();
    const [phonePeer] = await listPeers();
    await removePeer(phonePeer.deviceId);
    net.asPhone();
    await expect(syncNow()).resolves.toBeUndefined();
    expect(await listPeers()).toEqual([]);
  });

  it("does not pair when no pairing window is open", async () => {
    const net = connect(pc, phone);
    net.asComputer();
    stopPairing();
    net.asPhone();
    const [found] = await discover();
    await expect(pair(found, "123456")).rejects.toMatchObject({ code: "not-pairing" });
  });
});
