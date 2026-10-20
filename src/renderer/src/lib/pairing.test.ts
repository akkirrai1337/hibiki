import { describe, expect, it } from "vitest";
import type { SyncDevice } from "@shared/types";
import { pairedSince } from "./pairing";

const device = (deviceId: string, pairedAt: number): SyncDevice => ({ deviceId, name: deviceId, lastSyncAt: null, pairedAt, connects: false });

describe("pairedSince", () => {
  it("is false while the list holds only pairings from before the window opened", () => {
    expect(pairedSince([device("a", 1000), device("b", 2000)], 5000)).toBe(false);
    expect(pairedSince([], 5000)).toBe(false);
    expect(pairedSince(undefined, 5000)).toBe(false);
  });

  it("is true once a new device is in the list", () => {
    expect(pairedSince([device("a", 1000), device("b", 6000)], 5000)).toBe(true);
  });

  // Pairing again with a device already in the list replaces its entry: the list is no longer, but
  // its pairing time is newer.
  it("is true when a device that was already listed is paired again", () => {
    expect(pairedSince([device("a", 1000)], 5000)).toBe(false);
    expect(pairedSince([device("a", 5000)], 5000)).toBe(true);
  });
});
