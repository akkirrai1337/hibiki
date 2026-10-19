import { beforeEach, describe, expect, it, vi } from "vitest";

// The host's mobile-data signal, driven by the test.
const metered = vi.hoisted(() => ({ emit: (_value: boolean) => {} }));
vi.mock("@/lib/hibiki", () => ({
  hibiki: { network: { onMeteredChange: (callback: (value: boolean) => void) => { metered.emit = callback; } } },
}));

const { autoQualityCap, recordThroughput } = await import("./connectionQuality");
const { usePlayerPrefsStore } = await import("@/stores/playerPrefsStore");

/** A measurement of `mbps` from one 2 MB segment. */
const measure = (mbps: number) => recordThroughput(2_000_000, (2_000_000 * 8) / (mbps * 1000));

describe("autoQualityCap", () => {
  beforeEach(() => usePlayerPrefsStore.setState({ dataSaver: "auto" }));

  it("leaves a fast Wi-Fi line uncapped", () => {
    metered.emit(false);
    measure(40);
    expect(autoQualityCap()).toBeNull();
  });

  it("holds mobile data to 720p however fast it is, and before anything was measured", () => {
    metered.emit(true);
    expect(autoQualityCap()).toBe(720);
    measure(40);
    expect(autoQualityCap()).toBe(720);
  });

  it("still goes lower on a slow mobile line", () => {
    // A network change forgets the last line's measurement.
    metered.emit(false);
    metered.emit(true);
    measure(2);
    expect(autoQualityCap()).toBe(480);
  });

  it("is not applied with data saver off", () => {
    metered.emit(true);
    usePlayerPrefsStore.setState({ dataSaver: "off" });
    expect(autoQualityCap()).toBeNull();
  });
});
