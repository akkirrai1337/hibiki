// The pairing window: a device is findable for pairing only while one is open, the code is replaced
// every two minutes, and five tries (or a pairing, or ten minutes) close it for good.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import { deviceId } from "./changes";
import { discover, pair } from "./client";
import { DISCOVERY_PROBE, LOCATE_PROBE_PREFIX } from "./protocol";
import { answerProbe, isPairingOpen, pairingInfo, startPairing, stopPairing } from "./server";
import { connect, openDevice, type Db } from "./syncDevices.testkit";

let pc: Db;
let phone: Db;

beforeEach(async () => {
  pc = await openDevice();
  phone = await openDevice();
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
});

afterEach(() => {
  stopPairing();
  vi.useRealTimers();
});

const MINUTE = 60 * 1000;

/** The computer's platform with a recorder for what it is told about the window and the events it raises. */
function computer() {
  const events: string[] = [];
  const announced: boolean[] = [];
  installPlatform({
    db: { get: () => pc },
    events: { emit: (_channel: string, payload: unknown) => events.push((payload as { what: string }).what) },
    syncTransport: { setPairingOpen: (open: boolean) => void announced.push(open) },
  } as unknown as Platform);
  return { events, announced };
}

/** Another device's view of the computer, over the fake network. */
async function nearby() {
  const net = connect(pc, phone);
  net.asComputer();
  const { code } = startPairing();
  net.asPhone();
  return { net, code };
}

const wrongCode = (code: string) => (code === "000000" ? "111111" : "000000");

describe("who answers a search", () => {
  it("answers the search for devices to pair with only while a window is open", async () => {
    computer();
    expect(await answerProbe(DISCOVERY_PROBE, "PC", 47652, "computer")).toBeNull();
    startPairing();
    expect(await answerProbe(DISCOVERY_PROBE, "PC", 47652, "computer")).toMatchObject({ app: "hibiki", deviceId: await deviceId(), name: "PC", port: 47652, kind: "computer" });
    stopPairing();
    expect(await answerProbe(DISCOVERY_PROBE, "PC", 47652, "computer")).toBeNull();
  });

  it("answers a search for itself by id whether or not a window is open, and for nobody else", async () => {
    computer();
    const id = await deviceId();
    expect(await answerProbe(LOCATE_PROBE_PREFIX + id, "PC", 47652, "computer")).toMatchObject({ deviceId: id });
    expect(await answerProbe(LOCATE_PROBE_PREFIX + "someone-else", "PC", 47652, "computer")).toBeNull();
    expect(await answerProbe(LOCATE_PROBE_PREFIX, "PC", 47652, "computer")).toBeNull();
    expect(await answerProbe(LOCATE_PROBE_PREFIX + id + "x", "PC", 47652, "computer")).toBeNull();
    expect(await answerProbe("hello", "PC", 47652, "computer")).toBeNull();
  });

  it("is not found by a search for devices to pair with until the window is opened", async () => {
    const net = connect(pc, phone);
    net.asPhone();
    expect(await discover()).toEqual([]);
    net.asComputer();
    startPairing();
    net.asPhone();
    expect(await discover()).toHaveLength(1);
  });
});

describe("the code", () => {
  it("is replaced every two minutes, and the old one no longer pairs", async () => {
    const { net, code } = await nearby();
    const [found] = await discover();
    const first = pairingInfo()!;
    expect(first.code).toBe(code);
    expect(first.expiresAt - Date.now()).toBe(2 * MINUTE);

    // (A new code could equal the old one one time in a million; keep going until it differs.)
    let latest = first;
    for (let i = 0; i < 3 && latest.code === first.code; i++) {
      await vi.advanceTimersByTimeAsync(2 * MINUTE);
      latest = pairingInfo()!;
    }
    expect(latest.code).not.toBe(first.code);
    expect(latest.expiresAt).toBeGreaterThan(first.expiresAt);
    expect(net.events).toContain("pairing");

    await expect(pair(found, first.code)).rejects.toMatchObject({ code: "bad-code" });
    expect(await pair(found, latest.code)).toMatchObject({ name: "Test PC" });
  });

  it("changes without closing the window or giving the tries back", async () => {
    const { code } = await nearby();
    const [found] = await discover();
    for (let i = 0; i < 3; i++) await expect(pair(found, wrongCode(code))).rejects.toMatchObject({ code: "bad-code" });
    await vi.advanceTimersByTimeAsync(2 * MINUTE);
    expect(isPairingOpen()).toBe(true);
    const now = pairingInfo()!.code;
    for (let i = 0; i < 2; i++) await expect(pair(found, wrongCode(now))).rejects.toMatchObject({ code: "bad-code" });
    // Three before the new code and two after: five in all, so the sixth is turned away.
    await expect(pair(found, now)).rejects.toMatchObject({ code: "too-many-attempts" });
  });

  it("starts again with a full window and a new code when the window is opened twice", async () => {
    computer();
    const first = startPairing();
    await vi.advanceTimersByTimeAsync(9 * MINUTE);
    const second = startPairing();
    expect(second.closesAt).toBeGreaterThan(first.closesAt);
    // Past where the first window would have ended: the second is still open.
    await vi.advanceTimersByTimeAsync(9 * MINUTE);
    expect(isPairingOpen()).toBe(true);
  });
});

describe("closing", () => {
  it("closes after five tries - the fifth may still be completed, then the window goes", async () => {
    const { net, code } = await nearby();
    const [found] = await discover();
    for (let i = 0; i < 5; i++) await expect(pair(found, wrongCode(code))).rejects.toMatchObject({ code: "bad-code" });
    // The last try has been used but not yet given up on: a moment for it to be finished.
    expect(isPairingOpen()).toBe(true);
    await vi.advanceTimersByTimeAsync(16 * 1000);
    expect(isPairingOpen()).toBe(false);
    expect(pairingInfo()).toBeNull();
    expect(net.events.filter((e) => e === "pairing").length).toBeGreaterThanOrEqual(2);
    expect(await discover()).toEqual([]);
    await expect(pair(found, code)).rejects.toMatchObject({ code: "not-pairing" });
  });

  it("lets the fifth try succeed", async () => {
    const { code } = await nearby();
    const [found] = await discover();
    for (let i = 0; i < 4; i++) await expect(pair(found, wrongCode(code))).rejects.toMatchObject({ code: "bad-code" });
    const device = await pair(found, pairingInfo()!.code);
    expect(device.name).toBe("Test PC");
  });

  it("turns away a sixth try at once and closes", async () => {
    const { code } = await nearby();
    const [found] = await discover();
    for (let i = 0; i < 5; i++) await expect(pair(found, wrongCode(code))).rejects.toMatchObject({ code: "bad-code" });
    await expect(pair(found, code)).rejects.toMatchObject({ code: "too-many-attempts" });
    expect(isPairingOpen()).toBe(false);
  });

  it("closes once a device has paired, and is no longer found", async () => {
    const { net, code } = await nearby();
    const [found] = await discover();
    await pair(found, code);
    await vi.advanceTimersByTimeAsync(0);
    expect(isPairingOpen()).toBe(false);
    expect(net.events).toContain("pairing");
    expect(await discover()).toEqual([]);
  });

  it("closes on its own after ten minutes, however many codes it went through", async () => {
    const { net } = await nearby();
    await vi.advanceTimersByTimeAsync(9 * MINUTE);
    expect(isPairingOpen()).toBe(true);
    await vi.advanceTimersByTimeAsync(2 * MINUTE);
    expect(isPairingOpen()).toBe(false);
    expect(net.events.filter((e) => e === "pairing").length).toBeGreaterThan(3);
  });

  it("is closed by stopPairing, which is harmless when nothing is open", async () => {
    computer();
    expect(() => stopPairing()).not.toThrow();
    startPairing();
    stopPairing();
    expect(isPairingOpen()).toBe(false);
  });
});

describe("telling the network layer", () => {
  it("announces the window open and closed, whichever way it closes", async () => {
    const { announced } = computer();
    startPairing();
    stopPairing();
    startPairing();
    await vi.advanceTimersByTimeAsync(11 * MINUTE);
    expect(announced).toEqual([true, false, true, false]);
  });

  it("carries on when the network layer cannot be told", async () => {
    installPlatform({
      db: { get: () => pc },
      events: { emit: () => {} },
      syncTransport: { setPairingOpen: () => Promise.reject(new Error("plugin gone")) },
    } as unknown as Platform);
    expect(() => startPairing()).not.toThrow();
    expect(isPairingOpen()).toBe(true);
    await vi.advanceTimersByTimeAsync(0);
  });
});
