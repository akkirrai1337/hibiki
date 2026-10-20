// Device sync under the conditions it actually meets: a write landing while changes are being
// collected, a computer restored from an older copy, three devices, a line that breaks half way,
// requests that are replayed or forged, and random edits on every device that must still leave all
// of them identical. changes.test.ts covers the straight paths; this is for what goes wrong.
import { and, eq } from "drizzle-orm";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { dailyActivity, library, syncPeers, syncState, syncTombstones, titleRatings, watchProgress, xpEvents } from "../db/schema";
import { applyChanges, collectChanges, currentSeq, deviceId, type ChangeSet } from "./changes";
import type { SyncPairMode } from "@shared/types";
import { discover, forgetPeer, pair, syncNow } from "./client";
import { seal } from "./crypto";
import { listPeers } from "./peers";
import { handleSyncRequest, startPairing, stopPairing } from "./server";
import { connect, openDevice, on, push, type Db } from "./syncDevices.testkit";

type Net = ReturnType<typeof connect>;

let pc: Db;
let phone: Db;

beforeEach(async () => {
  pc = await openDevice();
  phone = await openDevice();
});

afterEach(() => {
  stopPairing();
  vi.restoreAllMocks();
});

// --- Building blocks ----------------------------------------------------------------------------------

const SEP = "\u001f";

const libRow = (animeId: string, category = "watching", at = 1000) => ({ sourceId: "s", animeId, category, addedAt: at, updatedAt: at, animeJson: "{}" });

const progressRow = (titleId: string, episodeId: string, positionMs: number, updatedAt: number, watched = false) => ({
  sourceId: "s", titleId, episodeId, episodeNumber: 1, groupId: "g", quality: null, translation: null, playerName: null,
  positionMs, durationMs: 1_400_000, watched, updatedAt, thumbnailDataUrl: null as string | null,
});

const emptyChanges = (): ChangeSet => ({ upTo: 0, more: false, library: [], progress: [], ratings: [], xp: [], activity: [], tombstones: [] });

const libraryIds = async (db: Db) => (await db.select().from(library).all()).map((row) => row.animeId).sort();

/** Everything a person can see on a device, as comparable text - no change numbers or local ids. */
async function snapshot(db: Db) {
  return {
    library: (await db.select().from(library).all()).map((r) => `${r.sourceId}/${r.animeId}:${r.category}@${r.updatedAt}`).sort(),
    progress: (await db.select().from(watchProgress).all()).map((r) => `${r.titleId}/${r.episodeId}:${r.positionMs}${r.watched ? "w" : ""}@${r.updatedAt}`).sort(),
    ratings: (await db.select().from(titleRatings).all()).map((r) => `${r.animeId}:${r.rating}@${r.ratedAt}`).sort(),
    xp: (await db.select().from(xpEvents).all()).map((r) => r.uid).sort(),
    activity: (await db.select().from(dailyActivity).all()).map((r) => `${r.date}/${r.deviceId}:${r.watchedMs}/${r.completedCount}`).sort(),
  };
}

/** Pairs the phone with the computer over `net`, then lets the first sync (started in the background by pairing) finish or fail. */
async function pairWith(net: Net, mode?: SyncPairMode) {
  net.asComputer();
  const { code } = startPairing();
  net.asPhone();
  const [found] = await discover();
  await pair(found, code, mode);
  await syncNow().catch(() => {});
  return net;
}

const paired = () => pairWith(connect(pc, phone));

/** One request as the computer sees it. */
async function ask(net: Net, request: unknown) {
  net.asComputer();
  return JSON.parse(await handleSyncRequest(JSON.stringify(request), "10.0.0.9:4000", "Test PC")) as { ok: boolean; error?: string; type?: string };
}

// --- The right answer, worked out without any sync ---------------------------------------------------------

/**
 * What every device must end up with, kept by the test itself as it makes the edits: the later edit of
 * a thing wins, a deletion included. Without it the devices could only be shown to agree with each
 * other - and they would agree just as well on a wrong answer.
 * (Whether an episode is "watched" is left out: it is sticky by design, which depends on the order
 * changes meet in rather than on the order they were made.)
 */
class Model {
  private library = new Map<string, string | null>();
  private ratings = new Map<string, string | null>();
  private progress = new Map<string, string | null>();
  private activity = new Map<string, string>();
  private xp = new Set<string>();

  set(kind: "library" | "ratings" | "progress", key: string, value: string | null) {
    this[kind].set(key, value);
  }
  addActivity(key: string, value: string) {
    this.activity.set(key, value);
  }
  addXp(uid: string) {
    this.xp.add(uid);
  }
  view() {
    const live = (map: Map<string, string | null>) => [...map.values()].filter((v): v is string => v !== null).sort();
    return { library: live(this.library), ratings: live(this.ratings), progress: live(this.progress), activity: [...this.activity.values()].sort(), xp: [...this.xp].sort() };
  }
}

/** The same view, read off a device. */
async function modelView(db: Db) {
  return {
    library: (await db.select().from(library).all()).map((r) => `${r.animeId}:${r.category}@${r.updatedAt}`).sort(),
    ratings: (await db.select().from(titleRatings).all()).map((r) => `${r.animeId}:${r.rating}@${r.ratedAt}`).sort(),
    progress: (await db.select().from(watchProgress).all()).map((r) => `${r.titleId}/${r.episodeId}:${r.positionMs}@${r.updatedAt}`).sort(),
    activity: (await db.select().from(dailyActivity).all()).map((r) => `${r.date}/${r.deviceId}:${r.watchedMs}`).sort(),
    xp: (await db.select().from(xpEvents).all()).map((r) => r.uid as string).sort(),
  };
}

// --- Random edits ---------------------------------------------------------------------------------------

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

interface Clock {
  t: number;
}

/**
 * One random edit on a device, dated by a clock shared by all of them and only ever moving forward -
 * so no two edits tie and there is always exactly one right answer for the devices to agree on.
 * A deletion's date is set by hand for the same reason (the trigger would write the wall clock).
 */
async function randomEdit(db: Db, device: string, rng: () => number, clock: Clock, model: Model) {
  const t = (clock.t += 1000);
  const pick = <T,>(items: T[]) => items[Math.floor(rng() * items.length)];
  const title = pick(["t0", "t1", "t2", "t3"]);
  const category = pick(["watching", "planned", "completed", "dropped"]);
  const inLibrary = and(eq(library.sourceId, "s"), eq(library.animeId, title));
  const episode = pick(["e0", "e1"]);
  const inProgress = and(eq(watchProgress.sourceId, "s"), eq(watchProgress.titleId, title), eq(watchProgress.episodeId, episode));
  const inRatings = and(eq(titleRatings.sourceId, "s"), eq(titleRatings.animeId, title));
  const redate = (tbl: string, key: string) => db.update(syncTombstones).set({ deletedAt: t }).where(and(eq(syncTombstones.tbl, tbl), eq(syncTombstones.key, key))).run();

  switch (Math.floor(rng() * 10)) {
    case 0:
    case 1: {
      if (await db.select().from(library).where(inLibrary).get()) await db.update(library).set({ category, updatedAt: t }).where(inLibrary).run();
      else await db.insert(library).values(libRow(title, category, t)).run();
      model.set("library", title, `${title}:${category}@${t}`);
      break;
    }
    case 2: {
      if (!(await db.select().from(library).where(inLibrary).get())) break;
      await db.delete(library).where(inLibrary).run();
      await redate("library", `s${SEP}${title}`);
      model.set("library", title, null);
      break;
    }
    case 3:
    case 4: {
      const positionMs = Math.floor(rng() * 1_000_000);
      const watched = rng() < 0.2;
      const row = await db.select().from(watchProgress).where(inProgress).get();
      // "Watched" is sticky on a device too: nothing in the app un-watches an episode.
      if (row) await db.update(watchProgress).set({ positionMs, watched: row.watched || watched, updatedAt: t }).where(inProgress).run();
      else await db.insert(watchProgress).values(progressRow(title, episode, positionMs, t, watched)).run();
      model.set("progress", `${title}/${episode}`, `${title}/${episode}:${positionMs}@${t}`);
      break;
    }
    case 5: {
      if (!(await db.select().from(watchProgress).where(inProgress).get())) break;
      await db.delete(watchProgress).where(inProgress).run();
      await redate("watch_progress", `s${SEP}${title}${SEP}${episode}`);
      model.set("progress", `${title}/${episode}`, null);
      break;
    }
    case 6: {
      const rating = 1 + Math.floor(rng() * 10);
      await db.insert(titleRatings).values({ sourceId: "s", animeId: title, rating, ratedAt: t }).onConflictDoUpdate({ target: [titleRatings.sourceId, titleRatings.animeId], set: { rating, ratedAt: t } }).run();
      model.set("ratings", title, `${title}:${rating}@${t}`);
      break;
    }
    case 7: {
      if (!(await db.select().from(titleRatings).where(inRatings).get())) break;
      await db.delete(titleRatings).where(inRatings).run();
      await redate("title_ratings", `s${SEP}${title}`);
      model.set("ratings", title, null);
      break;
    }
    case 8: {
      await db.insert(xpEvents).values({ kind: "k", xp: 5, createdAt: t, uid: `${device}-${t}` }).run();
      model.addXp(`${device}-${t}`);
      break;
    }
    default: {
      const date = `2026-10-0${1 + Math.floor(rng() * 3)}`;
      const own = and(eq(dailyActivity.date, date), eq(dailyActivity.deviceId, device));
      const before = await db.select().from(dailyActivity).where(own).get();
      const watchedMs = (before?.watchedMs ?? 0) + 1000 * (1 + Math.floor(rng() * 5));
      await db.insert(dailyActivity).values({ date, deviceId: device, watchedMs, completedCount: before?.completedCount ?? 0 }).onConflictDoUpdate({ target: [dailyActivity.date, dailyActivity.deviceId], set: { watchedMs } }).run();
      model.addActivity(`${date}/${device}`, `${date}/${device}:${watchedMs}`);
    }
  }
}

// --- The tests ------------------------------------------------------------------------------------------

describe("a write made while changes are being collected", () => {
  // The change counter is read after the tables were; a write in between got a number below the
  // batch's upper bound but was not in it, so the cursor moved past it and it was never sent.
  it.each([1, 2, 3, 4, 5, 6, 7])("is delivered by this batch or the next, wherever it lands (query %i)", async (landsAt) => {
    let queries = 0;
    let armed = false;
    let source!: Db;
    source = await openDevice(async () => {
      if (!armed || ++queries !== landsAt) return;
      armed = false;
      await source.insert(library).values(libRow("late", "planned", 5000)).run();
    });
    const target = await openDevice();

    await source.insert(library).values(libRow("early", "watching", 4000)).run();
    on(source);
    armed = true;
    const first = await collectChanges(0);
    armed = false;
    on(target);
    await applyChanges(first);
    on(source);
    const second = await collectChanges(first.upTo);
    on(target);
    await applyChanges(second);

    expect(await libraryIds(target)).toEqual(["early", "late"]);
  });
});

describe("a computer restored from an older copy", () => {
  // The phone remembers how far it has read the computer's changes. A restored computer's counter is
  // behind that, so everything it does next was numbered below the phone's mark and never offered.
  it("still gets its new changes to the phone, and is healed by the phone's copy", async () => {
    const net = await paired();
    net.asComputer();
    for (let i = 0; i < 30; i++) await pc.insert(library).values(libRow(`old${i}`, "planned", 100 + i)).run();
    net.asPhone();
    await syncNow();
    expect(await libraryIds(phone)).toHaveLength(30);

    // Back up and restore: the same device and pairing, but only part of what it had, and a counter to match.
    net.asComputer();
    const identity = await deviceId();
    const peers = await pc.select().from(syncPeers).all();
    const restored = await openDevice();
    await restored.update(syncState).set({ value: identity }).where(eq(syncState.key, "device_id")).run();
    for (const peer of peers) await restored.insert(syncPeers).values(peer).run();
    for (let i = 0; i < 3; i++) await restored.insert(library).values(libRow(`old${i}`, "planned", 100 + i)).run();
    await restored.insert(library).values(libRow("fresh", "watching", 9000)).run();

    const afterRestore = connect(restored, phone);
    afterRestore.asPhone();
    await syncNow();

    expect(await libraryIds(phone)).toContain("fresh");
    expect(await libraryIds(restored)).toHaveLength(31);
  });
});

describe("three devices", () => {
  it("carries a change from one phone to the other through the computer, and then goes quiet", async () => {
    const phone2 = await openDevice();
    const net1 = await paired();
    const net2 = await pairWith(connect(pc, phone2));

    await phone.insert(library).values(libRow("from-1", "watching", 1000)).run();
    await phone.insert(xpEvents).values({ kind: "one", xp: 5, createdAt: 1000 }).run();
    net1.asPhone();
    await syncNow();
    net2.asPhone();
    await syncNow();
    expect(await libraryIds(phone2)).toEqual(["from-1"]);

    // The second phone changes the same title later; the first gets it, via the computer.
    await phone2.update(library).set({ category: "completed", updatedAt: 2000 }).where(eq(library.animeId, "from-1")).run();
    net2.asPhone();
    await syncNow();
    net1.asPhone();
    await syncNow();
    expect((await phone.select().from(library).get())!.category).toBe("completed");

    net2.asPhone();
    await syncNow();
    expect(await snapshot(phone)).toEqual(await snapshot(pc));
    expect(await snapshot(phone2)).toEqual(await snapshot(pc));
    expect((await snapshot(pc)).xp).toHaveLength(1);

    // Nothing changed since: another round on each is idle - no ping-pong of the same rows.
    const dataEvents = () => net1.events.filter((e) => e === "data").length + net2.events.filter((e) => e === "data").length;
    const before = dataEvents();
    for (const net of [net1, net2, net1, net2]) {
      net.asPhone();
      await syncNow();
    }
    expect(dataEvents()).toBe(before);
    expect(await snapshot(phone)).toEqual(await snapshot(phone2));
  });
});

describe("random edits on every device", () => {
  const seeds = Array.from({ length: 30 }, (_, i) => i + 1);

  it.each(seeds)("leave three devices identical after they exchange everything (seed %i)", async (seed) => {
    const rng = mulberry32(seed);
    const clock = { t: 1_800_000_000_000 };
    const devices = [pc, phone, await openDevice()];
    const names = ["a", "b", "c"];
    const model = new Model();
    for (let step = 0; step < 60; step++) {
      const i = Math.floor(rng() * 3);
      if (rng() < 0.75) await randomEdit(devices[i], names[i], rng, clock, model);
      else await push(devices[i], devices[(i + 1 + Math.floor(rng() * 2)) % 3]);
    }
    for (let round = 0; round < 2; round++) for (const from of devices) for (const to of devices) if (from !== to) await push(from, to);

    const [a, b, c] = await Promise.all(devices.map(snapshot));
    expect(b, `seed ${seed}: b differs from a`).toEqual(a);
    expect(c, `seed ${seed}: c differs from a`).toEqual(a);
    for (const [name, db] of [["a", devices[0]], ["b", devices[1]], ["c", devices[2]]] as const) {
      expect(await modelView(db), `seed ${seed}: ${name} is not what the edits add up to`).toEqual(model.view());
    }
    // And a further full exchange changes nothing anywhere.
    for (const from of devices) {
      for (const to of devices) {
        if (from === to) continue;
        on(from);
        const batch = await collectChanges(0);
        on(to);
        expect((await applyChanges(batch)).changed, `seed ${seed}: still changing`).toBe(0);
      }
    }
  });

  it.each(seeds)("leave a phone and a computer identical when they sync at random moments (seed %i)", async (seed) => {
    const rng = mulberry32(1000 + seed);
    const clock = { t: 1_800_000_000_000 };
    const model = new Model();
    const net = await paired();
    for (let step = 0; step < 50; step++) {
      if (rng() < 0.7) {
        const onPhone = rng() < 0.5;
        await randomEdit(onPhone ? phone : pc, onPhone ? "phone" : "pc", rng, clock, model);
      } else {
        net.asPhone();
        await syncNow();
      }
    }
    net.asPhone();
    await syncNow();
    await syncNow();
    expect(await snapshot(pc), `seed ${seed}`).toEqual(await snapshot(phone));
    expect(await modelView(pc), `seed ${seed}: not what the edits add up to`).toEqual(model.view());
  });

  it.each(seeds.slice(0, 12))("leave two phones and a computer identical (seed %i)", async (seed) => {
    const rng = mulberry32(5000 + seed);
    const clock = { t: 1_800_000_000_000 };
    const phone2 = await openDevice();
    const net1 = await paired();
    const net2 = await pairWith(connect(pc, phone2));
    const devices = [{ db: pc, name: "pc" }, { db: phone, name: "p1" }, { db: phone2, name: "p2" }];
    const model = new Model();
    for (let step = 0; step < 60; step++) {
      if (rng() < 0.7) {
        const who = devices[Math.floor(rng() * 3)];
        await randomEdit(who.db, who.name, rng, clock, model);
      } else {
        (rng() < 0.5 ? net1 : net2).asPhone();
        await syncNow();
      }
    }
    for (const net of [net1, net2, net1, net2]) {
      net.asPhone();
      await syncNow();
    }
    expect(await snapshot(phone), `seed ${seed}: p1`).toEqual(await snapshot(pc));
    expect(await snapshot(phone2), `seed ${seed}: p2`).toEqual(await snapshot(pc));
    expect(await modelView(pc), `seed ${seed}: not what the edits add up to`).toEqual(model.view());
  });
});

describe("a request the computer should not trust", () => {
  it("answers nonsense and a different protocol version without acting on them", async () => {
    const net = await paired();
    net.asComputer();
    expect(JSON.parse(await handleSyncRequest("not json", "x", "pc"))).toEqual({ ok: false, error: "bad-message" });
    expect(JSON.parse(await handleSyncRequest("null", "x", "pc"))).toMatchObject({ ok: false });
    expect(await ask(net, { v: 99, type: "sync", from: "a", sealed: { iv: "", data: "" } })).toEqual({ ok: false, error: "version" });
    expect(await ask(net, { v: 1, type: "launch-missiles" })).toEqual({ ok: false, error: "bad-message" });
  });

  it("refuses a sync from a device it is not paired with", async () => {
    const net = await paired();
    expect(await ask(net, { v: 1, type: "sync", from: "nobody", sealed: await seal(new Uint8Array(32), { want: 0, changes: emptyChanges() }) })).toEqual({ ok: false, error: "unknown-device" });
  });

  it("will not wipe or change its data for a sync sealed with some other key", async () => {
    const net = await paired();
    net.asComputer();
    await pc.insert(library).values(libRow("mine", "watching", 1000)).run();
    net.asPhone();
    const phoneId = await deviceId();
    const wrongKey = new Uint8Array(32).fill(9);
    const forged = {
      v: 1,
      type: "sync",
      from: phoneId,
      sealed: await seal(wrongKey, { want: 0, replace: true, changes: { ...emptyChanges(), library: [{ ...libRow("planted", "watching", 2000) }] } }),
    };
    expect(await ask(net, forged)).toEqual({ ok: false, error: "bad-message" });
    expect(await libraryIds(pc)).toEqual(["mine"]);
  });

  it("does not let an old request, replayed, roll newer data back", async () => {
    const net = await paired();
    await phone.insert(library).values(libRow("a", "watching", 1000)).run();
    await phone.insert(watchProgress).values(progressRow("a", "e1", 100_000, 1000)).run();
    net.asPhone();
    await syncNow();
    const captured = net.state.requests.filter((message) => JSON.parse(message).type === "sync").at(-1)!;

    // Later, on the computer: a newer category and a newer position.
    await pc.update(library).set({ category: "completed", updatedAt: 3000 }).where(eq(library.animeId, "a")).run();
    await pc.update(watchProgress).set({ positionMs: 900_000, updatedAt: 3000 }).where(eq(watchProgress.titleId, "a")).run();
    net.asComputer();
    const seq = await currentSeq();

    const replayed = JSON.parse(await handleSyncRequest(captured, "10.0.0.5:1", "pc")) as { ok: boolean };
    expect(replayed.ok).toBe(true);
    expect((await pc.select().from(library).get())!.category).toBe("completed");
    expect((await pc.select().from(watchProgress).get())!.positionMs).toBe(900_000);
    expect(await currentSeq()).toBe(seq);
  });

  it("does not take a device for paired because it said hello", async () => {
    const net = connect(pc, phone);
    net.state.failType = "pair-confirm";
    net.asComputer();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    await expect(pair(found, code)).rejects.toMatchObject({ code: "unreachable" });

    net.asPhone();
    const phoneId = await deviceId();
    expect(await ask(net, { v: 1, type: "sync", from: phoneId, sealed: await seal(new Uint8Array(32), { want: 0, changes: emptyChanges() }) })).toEqual({ ok: false, error: "unknown-device" });
    expect(await listPeers()).toEqual([]);
    net.asComputer();
    expect(await listPeers()).toEqual([]);
    expect(await ask(net, { v: 1, type: "pair-confirm", from: "stranger", proof: "x" })).toEqual({ ok: false, error: "not-pairing" });
  });
});

describe("the pairing window", () => {
  async function discovered() {
    const net = connect(pc, phone);
    net.asComputer();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    return { net, code, found };
  }

  it("closes when its time runs out", async () => {
    const { code, found } = await discovered();
    const now = Date.now();
    vi.spyOn(Date, "now").mockReturnValue(now + 11 * 60 * 1000);
    await expect(pair(found, code)).rejects.toMatchObject({ code: "not-pairing" });
  });

  it("closes after five tries, even for the right code", async () => {
    const { code, found } = await discovered();
    const wrong = code === "000000" ? "111111" : "000000";
    for (let i = 0; i < 5; i++) await expect(pair(found, wrong)).rejects.toMatchObject({ code: "bad-code" });
    await expect(pair(found, code)).rejects.toMatchObject({ code: "too-many-attempts" });
    await expect(pair(found, code)).rejects.toMatchObject({ code: "not-pairing" });
  });

  it("works once: a second device cannot use the code that has just paired the first", async () => {
    const { code, found, net } = await discovered();
    await pair(found, code);
    await syncNow().catch(() => {});
    const phone2 = await openDevice();
    const net2 = connect(pc, phone2);
    net2.asPhone();
    await expect(pair(found, code)).rejects.toMatchObject({ code: "not-pairing" });
    net.asComputer();
    expect(await listPeers()).toHaveLength(1);
  });
});

describe("a line that breaks", () => {
  it("leaves both devices as they were, and catches up once it is back", async () => {
    const net = await paired();
    await phone.insert(library).values(libRow("p", "watching", 1000)).run();
    await pc.insert(library).values(libRow("c", "planned", 1000)).run();
    const before = await snapshot(phone);

    net.state.reachable = false;
    net.asPhone();
    await expect(syncNow()).rejects.toMatchObject({ code: "unreachable" });
    expect(await snapshot(phone)).toEqual(before);
    expect(await libraryIds(pc)).toEqual(["c"]);

    net.state.reachable = true;
    await syncNow();
    expect(await libraryIds(phone)).toEqual(["c", "p"]);
    expect(await libraryIds(pc)).toEqual(["c", "p"]);
  });

  it("does not apply anything twice when the answer is lost after the computer acted", async () => {
    const net = await paired();
    await phone.insert(library).values(libRow("p", "watching", 1000)).run();
    await phone.insert(xpEvents).values({ kind: "one", xp: 5, createdAt: 1000 }).run();
    await pc.insert(library).values(libRow("c", "planned", 1000)).run();

    net.state.loseAnswerOf = "sync";
    net.asPhone();
    await expect(syncNow()).rejects.toMatchObject({ code: "unreachable" });
    expect(await libraryIds(pc)).toEqual(["c", "p"]);

    net.state.loseAnswerOf = null;
    await syncNow();
    expect(await snapshot(phone)).toEqual(await snapshot(pc));
    expect((await snapshot(pc)).xp).toHaveLength(1);
  });

  it("resumes a large sync where it stopped instead of losing or repeating it", async () => {
    const net = connect(pc, phone);
    for (let i = 0; i < 900; i++) await phone.insert(library).values(libRow(`b${i}`, "planned", 100 + i)).run();
    // Pairing (two requests), then two rounds of the first sync get through; the next one does not.
    net.state.budget = 4;
    await pairWith(net);
    const [peer] = await phone.select().from(syncPeers).all();
    expect(peer.sentSeq).toBeGreaterThan(0);
    expect((await libraryIds(pc)).length).toBeGreaterThan(0);
    expect((await libraryIds(pc)).length).toBeLessThan(900);

    net.state.budget = null;
    net.asPhone();
    await syncNow();
    expect(await libraryIds(pc)).toHaveLength(900);
    expect(await snapshot(phone)).toEqual(await snapshot(pc));
  });

  it("finds the computer again when its address has changed", async () => {
    const net = await paired();
    await phone.insert(library).values(libRow("x", "watching", 1000)).run();
    net.state.deadHosts.add("10.0.0.2");
    net.state.computerHost = "10.0.0.9";
    net.asPhone();
    await syncNow();
    expect(await libraryIds(pc)).toEqual(["x"]);
    const [peer] = await phone.select().from(syncPeers).all();
    expect(peer.lastAddress).toMatch(/^10\.0\.0\.9:/);
  });

  it("forgets a device locally even when the other one cannot be told", async () => {
    const net = await paired();
    net.state.reachable = false;
    net.asPhone();
    const [peer] = await listPeers();
    await expect(forgetPeer(peer.deviceId)).resolves.toBeUndefined();
    expect(await listPeers()).toEqual([]);
  });

  it("does not empty a device that was to take the other's data until that one has answered", async () => {
    const net = connect(pc, phone);
    await phone.insert(library).values(libRow("mine", "watching", 1000)).run();
    await pc.insert(library).values(libRow("theirs", "planned", 1000)).run();
    net.state.failType = "sync";
    await pairWith(net, "take-there");
    expect(await libraryIds(phone)).toEqual(["mine"]);

    net.state.failType = null;
    net.asPhone();
    await syncNow();
    expect(await libraryIds(phone)).toEqual(["theirs"]);
    expect(await libraryIds(pc)).toEqual(["theirs"]);
  });

  it("finishes a replacement whose answer was lost, ending with the same data on both", async () => {
    const net = connect(pc, phone);
    await phone.insert(library).values(libRow("mine", "watching", 1000)).run();
    await pc.insert(library).values(libRow("theirs", "planned", 1000)).run();
    net.state.loseAnswerOf = "sync";
    await pairWith(net, "keep-here");

    net.state.loseAnswerOf = null;
    net.asPhone();
    await syncNow();
    expect(await libraryIds(phone)).toEqual(["mine"]);
    expect(await libraryIds(pc)).toEqual(["mine"]);
  });

  it("takes a change made while a sync was running on the run after it", async () => {
    const net = await paired();
    net.asPhone();
    const running = syncNow();
    await phone.insert(library).values(libRow("during", "watching", 1000)).run();
    const next = syncNow();
    await Promise.all([running, next]);
    expect(await libraryIds(pc)).toEqual(["during"]);
  });
});

describe("a lot of data at once", () => {
  it("brings every table across when several overflow a batch together", async () => {
    await paired();
    const thumbnail = `data:image/jpeg;base64,${"A".repeat(20_000)}`;
    for (let i = 0; i < 450; i++) {
      await phone.insert(library).values(libRow(`l${i}`, "planned", 100 + i)).run();
      await phone.insert(titleRatings).values({ sourceId: "s", animeId: `l${i}`, rating: 1 + (i % 10), ratedAt: 100 + i }).run();
    }
    for (let i = 0; i < 100; i++) await phone.insert(watchProgress).values({ ...progressRow(`l${i}`, "e1", i, 100 + i), thumbnailDataUrl: thumbnail }).run();

    const net = connect(pc, phone);
    // A fresh connection to the same two databases: the pairing above lives in them already.
    net.asPhone();
    await syncNow();

    expect(await libraryIds(pc)).toHaveLength(450);
    expect(await pc.select().from(titleRatings).all()).toHaveLength(450);
    const progress = await pc.select().from(watchProgress).all();
    expect(progress).toHaveLength(100);
    expect(progress.every((row) => row.thumbnailDataUrl === thumbnail)).toBe(true);
    expect(await snapshot(phone)).toEqual(await snapshot(pc));
  });
});
