// A device does not send back the rows the other one has just sent it (KnownVersions) - without ever
// leaving out a row the other side still needs. robustness.test.ts runs the same code under random
// edits and checks the devices end up right; this checks what actually travels.
import { eq } from "drizzle-orm";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { SyncPairMode } from "@shared/types";
import { library, syncPeers, syncState, watchProgress } from "../db/schema";
import { deviceId, KnownVersions, type ChangeSet } from "./changes";
import { discover, pair, syncNow } from "./client";
import { open, type Sealed } from "./crypto";
import { peerKey } from "./peers";
import type { SyncAnswer, SyncPayload } from "./protocol";
import { startPairing, stopPairing } from "./server";
import { connect, openDevice, type Db } from "./syncDevices.testkit";

type Net = ReturnType<typeof connect>;

let pc: Db;
let phone: Db;

beforeEach(async () => {
  pc = await openDevice();
  phone = await openDevice();
});

afterEach(() => stopPairing());

const libRow = (animeId: string, category = "planned", at = 100) => ({ sourceId: "s", animeId, category, addedAt: at, updatedAt: at, animeJson: "{}" });
const progressRow = (titleId: string, positionMs: number, updatedAt: number, watched = false) => ({
  sourceId: "s", titleId, episodeId: "e1", episodeNumber: 1, groupId: "g", quality: null, translation: null, playerName: null,
  positionMs, durationMs: 1_400_000, watched, updatedAt, thumbnailDataUrl: null as string | null,
});
const empty = (): ChangeSet => ({ upTo: 0, more: false, library: [], progress: [], ratings: [], xp: [], activity: [], tombstones: [] });
const ids = async (db: Db) => (await db.select().from(library).all()).map((r) => r.animeId).sort();

async function paired(mode?: SyncPairMode) {
  const net = connect(pc, phone);
  net.asComputer();
  const { code } = startPairing();
  net.asPhone();
  const [found] = await discover();
  await pair(found, code, mode);
  await syncNow().catch(() => {});
  return net;
}

/** What each sync request carried and what it was answered with, opened with the pair's key. */
async function syncsOn(net: Net) {
  net.asPhone();
  const [peer] = await phone.select().from(syncPeers).all();
  const key = await peerKey(peer);
  const all = net.state.requests.map((message, i) => ({ request: JSON.parse(message) as { type: string; sealed: Sealed }, answer: net.state.answers[i] }));
  return Promise.all(
    all.filter((m) => m.request.type === "sync").map(async ({ request, answer }) => ({
      sent: (await open<SyncPayload>(key, request.sealed)).changes,
      got: (await open<SyncAnswer>(key, (JSON.parse(answer) as { sealed: Sealed }).sealed)).changes,
    })),
  );
}

describe("not sending back what the other device has just sent", () => {
  const thumbnail = `data:image/jpeg;base64,${"A".repeat(5000)}`;

  it("does not answer a batch with the same rows it was just given", async () => {
    const net = await paired();
    for (let i = 0; i < 5; i++) await phone.insert(library).values(libRow(`p${i}`, "planned", 100 + i)).run();
    await phone.insert(watchProgress).values({ ...progressRow("p0", 5000, 200), thumbnailDataUrl: thumbnail }).run();
    net.asPhone();
    await syncNow();

    const last = (await syncsOn(net)).at(-1)!;
    expect(last.sent.library).toHaveLength(5);
    expect(last.got.library).toEqual([]);
    expect(last.got.progress).toEqual([]);
    expect(last.got.titles ?? []).toEqual([]);
    expect(await ids(pc)).toHaveLength(5);
  });

  it("does not send the computer's rows back to it on the next sync", async () => {
    const net = await paired();
    for (let i = 0; i < 4; i++) await pc.insert(library).values(libRow(`c${i}`, "planned", 100 + i)).run();
    net.asPhone();
    await syncNow();
    expect(await ids(phone)).toHaveLength(4);

    await syncNow();
    const last = (await syncsOn(net)).at(-1)!;
    expect(last.sent.library).toEqual([]);
    expect(last.got.library).toEqual([]);
  });

  it("still sends a row the other device holds in an older or different version", async () => {
    const net = await paired();
    await phone.insert(library).values(libRow("a", "watching", 1000)).run();
    await phone.insert(watchProgress).values(progressRow("a", 1000, 1000)).run();
    net.asPhone();
    await syncNow();

    // The computer then moves on: a newer category, and the episode marked watched.
    await pc.update(library).set({ category: "completed", updatedAt: 3000 }).where(eq(library.animeId, "a")).run();
    await pc.update(watchProgress).set({ watched: true, updatedAt: 3000 }).where(eq(watchProgress.titleId, "a")).run();
    await syncNow();
    expect((await phone.select().from(library).get())!.category).toBe("completed");
    expect((await phone.select().from(watchProgress).get())!.watched).toBe(true);

    // And a change made here right after something arrived is not mistaken for the arrival.
    await phone.update(library).set({ category: "dropped", updatedAt: 4000 }).where(eq(library.animeId, "a")).run();
    await syncNow();
    expect((await pc.select().from(library).get())!.category).toBe("dropped");
  });

  it("checks the other device first before leaving anything out, then sends only what is new", async () => {
    const net = await paired();
    await pc.insert(library).values(libRow("c", "planned", 100)).run();
    net.asPhone();
    await syncNow();
    await phone.insert(library).values(libRow("p", "planned", 200)).run();
    const before = (await syncsOn(net)).length;
    await syncNow();

    const sent = (await syncsOn(net)).slice(before);
    // A first request that only asks, then one carrying the new row alone - not the computer's own.
    expect(sent.map((m) => m.sent.library.map((r) => r.animeId))).toEqual([[], ["p"]]);
    expect(await ids(pc)).toEqual(["c", "p"]);
  });

  it("does not trust its memory of a computer whose database went back in time", async () => {
    // The phone remembers what it was sent; those rows are exactly what a restored copy is missing,
    // and they must still reach it.
    const net = await paired();
    net.asComputer();
    for (let i = 0; i < 12; i++) await pc.insert(library).values(libRow(`old${i}`, "planned", 100 + i)).run();
    net.asPhone();
    await syncNow();
    await syncNow();
    net.asComputer();
    const identity = await deviceId();
    const peers = await pc.select().from(syncPeers).all();
    const restored = await openDevice();
    await restored.update(syncState).set({ value: identity }).where(eq(syncState.key, "device_id")).run();
    for (const peer of peers) await restored.insert(syncPeers).values(peer).run();

    connect(restored, phone).asPhone();
    await syncNow();
    expect(await ids(restored)).toHaveLength(12);
  });

  it("starts from nothing after a replacement, so the new data is sent in full", async () => {
    const net = await paired();
    for (let i = 0; i < 3; i++) await pc.insert(library).values(libRow(`c${i}`, "planned", 100 + i)).run();
    net.asPhone();
    await syncNow();
    // Pair again, this time keeping the phone's data: it replaces what the computer holds.
    net.asComputer();
    const { code } = startPairing();
    net.asPhone();
    const [found] = await discover();
    await pair(found, code, "keep-here");
    await syncNow().catch(() => {});
    expect(await ids(pc)).toEqual(await ids(phone));
    expect(await ids(pc)).toHaveLength(3);
  });
});

describe("KnownVersions", () => {
  const batch = (overrides: Partial<ChangeSet> = {}): ChangeSet => ({ ...empty(), upTo: 9, more: true, ...overrides });
  const lib = (animeId: string, category = "planned", updatedAt = 100) => libRow(animeId, category, updatedAt) as ChangeSet["library"][number];
  const prog = (titleId: string, over: Partial<ChangeSet["progress"][number]> = {}) => ({ ...progressRow(titleId, 10, 100), ...over }) as ChangeSet["progress"][number];

  it("leaves out exactly the versions it was told of, and keeps the batch's bounds", () => {
    const known = new KnownVersions();
    known.remember(batch({ library: [lib("a"), lib("b")] }));
    const out = known.without(batch({ library: [lib("a"), lib("b"), lib("c")] }));
    expect(out.library.map((r) => r.animeId)).toEqual(["c"]);
    expect(out.upTo).toBe(9);
    expect(out.more).toBe(true);
  });

  it("treats a different category, time, watched flag or thumbnail as a different version", () => {
    const known = new KnownVersions();
    known.remember(batch({ library: [lib("a")], progress: [prog("a", { thumbnailDataUrl: "data:one" })] }));
    expect(known.without(batch({ library: [lib("a", "completed")] })).library).toHaveLength(1);
    expect(known.without(batch({ library: [lib("a", "planned", 101)] })).library).toHaveLength(1);
    expect(known.without(batch({ progress: [prog("a", { thumbnailDataUrl: "data:two" })] })).progress).toHaveLength(1);
    expect(known.without(batch({ progress: [prog("a", { thumbnailDataUrl: "data:one", watched: true })] })).progress).toHaveLength(1);
    expect(known.without(batch({ progress: [prog("a", { thumbnailDataUrl: "data:one" })] })).progress).toHaveLength(0);
  });

  it("drops the card of a title when nothing about it is left to send, and keeps it otherwise", () => {
    const known = new KnownVersions();
    known.remember(batch({ progress: [prog("a")] }));
    const card = (animeId: string) => ({ sourceId: "s", animeId, animeJson: "{}", cachedAt: 1 });
    const out = known.without(batch({ progress: [prog("a"), prog("b")], titles: [card("a"), card("b")] }));
    expect(out.titles!.map((c) => c.animeId)).toEqual(["b"]);
  });

  it("forgets everything when asked", () => {
    const known = new KnownVersions();
    known.remember(batch({ library: [lib("a")] }));
    known.clear();
    expect(known.without(batch({ library: [lib("a")] })).library).toHaveLength(1);
  });
});
