// The computer's real TCP server for device sync (main/sync.ts) driven by the real client transport
// (platform/electron/syncTransport.ts) over loopback, both ends doing the actual framing - what the
// in-process tests of core/sync skip. Two in-memory databases stand in for the two devices.
import net, { type AddressInfo } from "node:net";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { library, titleRatings, watchProgress } from "../core/db/schema";
import { deviceId } from "../core/sync/changes";
import { discover, pair, syncNow } from "../core/sync/client";
import { MAX_MESSAGE_BYTES, frame } from "../core/sync/protocol";
import { startPairing, stopPairing } from "../core/sync/server";
import { openDevice, type Db } from "../core/sync/syncDevices.testkit";
import { installPlatform } from "../core/platform";
import { electronSyncTransport } from "../platform/electron/syncTransport";
import type { Platform } from "../platform/types";
import { serveConnection } from "./sync";

vi.mock("../core/logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

let pc: Db;
let phone: Db;
let server: net.Server;
let port: number;

beforeEach(async () => {
  pc = await openDevice();
  phone = await openDevice();
  server = net.createServer(serveConnection);
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  port = (server.address() as AddressInfo).port;
});

afterEach(async () => {
  stopPairing();
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

const base = {
  secureStore: { isAvailable: async () => true, encrypt: async (text: string) => `x${text}`, decrypt: async (text: string) => text.slice(1) },
  events: { emit: () => {} },
};
const asComputer = () => installPlatform({ ...base, db: { get: () => pc } } as unknown as Platform);

/** The phone, reaching the computer through the real transport; the computer's database is what the server sees while a request is out. */
const asPhone = () =>
  installPlatform({
    ...base,
    db: { get: () => phone },
    syncTransport: {
      deviceName: async () => "Test phone",
      discover: async () => {
        asComputer();
        const id = await deviceId();
        asPhone();
        return [{ deviceId: id, name: "Test PC", host: "127.0.0.1", port }];
      },
      request: async (_host: string, _port: number, message: string, timeoutMs: number) => {
        asComputer();
        try {
          return await electronSyncTransport.request("127.0.0.1", port, message, timeoutMs);
        } finally {
          asPhone();
        }
      },
    },
  } as unknown as Platform);

async function pairPhone() {
  asComputer();
  const { code } = startPairing();
  asPhone();
  const [found] = await discover();
  await pair(found, code);
  await syncNow().catch(() => {});
}

const row = (animeId: string, at: number) => ({ sourceId: "s", animeId, category: "planned", addedAt: at, updatedAt: at, animeJson: "{}" });
const ids = async (db: Db) => (await db.select().from(library).all()).map((r) => r.animeId).sort();

describe("device sync over a real socket", () => {
  it("pairs and syncs both ways", async () => {
    await phone.insert(library).values(row("from-phone", 100)).run();
    await pc.insert(library).values(row("from-pc", 200)).run();
    await pairPhone();
    asPhone();
    await syncNow();
    expect(await ids(phone)).toEqual(["from-pc", "from-phone"]);
    expect(await ids(pc)).toEqual(["from-pc", "from-phone"]);
  });

  it("carries a few megabytes in one go, and many batches in a row", async () => {
    const thumbnail = `data:image/jpeg;base64,${"A".repeat(40_000)}`;
    for (let i = 0; i < 80; i++) {
      await phone
        .insert(watchProgress)
        .values({ sourceId: "s", titleId: `t${i}`, episodeId: "e1", episodeNumber: 1, groupId: "g", quality: null, translation: null, playerName: null, positionMs: i, durationMs: 1000, watched: false, updatedAt: 100 + i, thumbnailDataUrl: thumbnail })
        .run();
    }
    for (let i = 0; i < 700; i++) await phone.insert(titleRatings).values({ sourceId: "s", animeId: `a${i}`, rating: 1 + (i % 10), ratedAt: 100 + i }).run();
    await pairPhone();
    asPhone();
    await syncNow();
    expect(await pc.select().from(watchProgress).all()).toHaveLength(80);
    expect(await pc.select().from(titleRatings).all()).toHaveLength(700);
  });

  it("serves two phones, each paired and syncing through the same server", async () => {
    const phone1 = phone;
    const phone2 = await openDevice();
    await phone1.insert(library).values(row("one", 100)).run();
    await phone2.insert(library).values(row("two", 100)).run();

    await pairPhone();
    phone = phone2;
    await pairPhone();

    for (const device of [phone1, phone2, phone1]) {
      phone = device;
      asPhone();
      await syncNow();
    }
    expect(await ids(pc)).toEqual(["one", "two"]);
    expect(await ids(phone1)).toEqual(["one", "two"]);
    expect(await ids(phone2)).toEqual(["one", "two"]);
  });
});

describe("the server, handed what a network hands it", () => {
  /** Sends `chunks` on one connection and resolves with what comes back before it closes. */
  function talk(chunks: Buffer[], gapMs = 5): Promise<{ answer: Buffer; closed: boolean }> {
    return new Promise((resolve) => {
      const socket = net.connect({ host: "127.0.0.1", port });
      let answer = Buffer.alloc(0);
      socket.on("data", (chunk: Buffer) => (answer = Buffer.concat([answer, chunk])));
      socket.on("error", () => {});
      socket.on("close", () => resolve({ answer, closed: true }));
      void (async () => {
        for (const chunk of chunks) {
          socket.write(chunk);
          await new Promise((r) => setTimeout(r, gapMs));
        }
      })();
      setTimeout(() => {
        socket.destroy();
        resolve({ answer, closed: false });
      }, 1500);
    });
  }

  const hello = JSON.stringify({ v: 1, type: "pair-confirm", from: "x", proof: "y" });
  const answerText = (answer: Buffer) => answer.subarray(4, 4 + answer.readUInt32BE(0)).toString("utf-8");

  beforeEach(() => {
    asComputer();
  });

  it("puts a request back together from pieces, even when the length arrives split", async () => {
    const whole = Buffer.from(frame(hello));
    const { answer } = await talk([whole.subarray(0, 2), whole.subarray(2, 5), whole.subarray(5, 9), whole.subarray(9)]);
    expect(JSON.parse(answerText(answer))).toEqual({ ok: false, error: "not-pairing" });
  });

  it("takes a request one byte at a time", async () => {
    const whole = Buffer.from(frame(hello));
    const { answer } = await talk([...whole].map((byte) => Buffer.from([byte])), 0);
    expect(JSON.parse(answerText(answer))).toEqual({ ok: false, error: "not-pairing" });
  });

  it("drops a connection that announces a message larger than allowed, and carries on serving", async () => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(MAX_MESSAGE_BYTES + 1);
    const { answer, closed } = await talk([length]);
    expect(closed).toBe(true);
    expect(answer.length).toBe(0);
    const next = await talk([Buffer.from(frame(hello))]);
    expect(JSON.parse(answerText(next.answer))).toEqual({ ok: false, error: "not-pairing" });
  });

  it("survives a client that connects and leaves, or leaves in the middle of a request", async () => {
    await talk([]);
    const whole = Buffer.from(frame(hello));
    await new Promise<void>((resolve) => {
      const socket = net.connect({ host: "127.0.0.1", port }, () => {
        socket.write(whole.subarray(0, 6));
        socket.destroy();
        setTimeout(resolve, 50);
      });
      socket.on("error", () => {});
    });
    const next = await talk([whole]);
    expect(JSON.parse(answerText(next.answer))).toEqual({ ok: false, error: "not-pairing" });
  });

  it("answers an unreadable request with an error instead of dropping it", async () => {
    const { answer } = await talk([Buffer.from(frame("not json at all"))]);
    expect(JSON.parse(answerText(answer))).toEqual({ ok: false, error: "bad-message" });
  });

  it("answers only the first request of a connection", async () => {
    const one = Buffer.from(frame(hello));
    const { answer } = await talk([Buffer.concat([one, one])]);
    expect(answer.length).toBe(4 + answer.readUInt32BE(0));
  });
});
