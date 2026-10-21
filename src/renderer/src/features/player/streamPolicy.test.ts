import { describe, expect, it } from "vitest";
import { clampToDuration, DashErrorPolicy, MAX_NETWORK_RETRIES, stallVerdict, startPosition, StreamErrorPolicy, type StreamError } from "./streamPolicy";

const network = (overrides: Partial<StreamError> = {}): StreamError => ({ fatal: true, kind: "network", details: "fragLoadError", manifest: false, ...overrides });

describe("stream error policy", () => {
  it("retries a network blip with a growing backoff, then gives up", () => {
    const policy = new StreamErrorPolicy();
    const delays = Array.from({ length: MAX_NETWORK_RETRIES }, () => policy.decide(network(), false));
    expect(delays.map((action) => action.kind === "retry" && action.delayMs)).toEqual([500, 1000, 2000]);
    expect(policy.decide(network(), false)).toMatchObject({ kind: "fail" });
  });

  it("does not retry a status the server answered for good", () => {
    expect(new StreamErrorPolicy().decide(network({ status: 404 }), false)).toEqual({ kind: "fail", reason: "fragLoadError", answeredStatus: 404 });
  });

  it("retries an edge hiccup (500, 52x) like a blip", () => {
    expect(new StreamErrorPolicy().decide(network({ status: 500 }), false).kind).toBe("retry");
    expect(new StreamErrorPolicy().decide(network({ status: 522 }), false).kind).toBe("retry");
  });

  it("reloads the manifest when it was the manifest that failed", () => {
    expect(new StreamErrorPolicy().decide(network({ manifest: true }), false)).toMatchObject({ kind: "retry", reloadManifest: true });
  });

  it("a segment buffered gives the retry budget back", () => {
    const policy = new StreamErrorPolicy();
    for (let i = 0; i < MAX_NETWORK_RETRIES; i++) policy.decide(network(), false);
    policy.fragmentBuffered();
    expect(policy.decide(network(), false).kind).toBe("retry");
  });

  it("renews an expired token mid-play once, then lets a second refusal fail over", () => {
    const policy = new StreamErrorPolicy();
    expect(policy.decide(network({ status: 403, fatal: false }), true)).toEqual({ kind: "renew", reason: "fragLoadError" });
    // hls.js keeps retrying while the renewal is out; nothing to do about it.
    expect(policy.decide(network({ status: 403, fatal: false }), true)).toEqual({ kind: "ignore" });
    expect(policy.decide(network({ status: 403, fatal: true }), true)).toEqual({ kind: "ignore" });
    expect(policy.renewalSettled()).toBe(true);
    // Renewed, and refused again before a single segment loaded: not a token problem.
    expect(policy.decide(network({ status: 403 }), true)).toMatchObject({ kind: "fail", answeredStatus: 403 });
  });

  it("a renewal that is followed by a loaded segment can happen again later", () => {
    const policy = new StreamErrorPolicy();
    policy.decide(network({ status: 403, fatal: false }), true);
    expect(policy.renewalSettled()).toBe(false);
    policy.fragmentLoaded();
    expect(policy.decide(network({ status: 403, fatal: false }), true).kind).toBe("renew");
  });

  it("never renews a refused manifest, or a stream that has not played yet", () => {
    expect(new StreamErrorPolicy().decide(network({ status: 403, manifest: true, manifestLoadError: true }), true).kind).toBe("fail");
    expect(new StreamErrorPolicy().decide(network({ status: 403 }), false).kind).toBe("fail");
  });

  it("gives up on a segment that keeps coming back empty", () => {
    const policy = new StreamErrorPolicy();
    const empty: StreamError = { fatal: false, kind: "media", details: "fragParsingError", manifest: false, emptyFragment: "12" };
    expect([1, 2, 3].map(() => policy.decide(empty, false).kind)).toEqual(["ignore", "ignore", "ignore"]);
    expect(policy.decide(empty, false)).toEqual({ kind: "fail", reason: "fragment 12 has no media" });
  });

  it("counts empty repeats per segment", () => {
    const policy = new StreamErrorPolicy();
    const empty = (sn: string): StreamError => ({ fatal: false, kind: "media", details: "fragParsingError", manifest: false, emptyFragment: sn });
    for (let i = 0; i < 3; i++) policy.decide(empty("1"), false);
    expect(policy.decide(empty("2"), false).kind).toBe("ignore");
  });

  it("recovers a media error a few times, then fails over", () => {
    const policy = new StreamErrorPolicy();
    const media: StreamError = { fatal: true, kind: "media", details: "bufferStalledError", manifest: false };
    expect([1, 2, 3].map(() => policy.decide(media, false).kind)).toEqual(["recoverMedia", "recoverMedia", "recoverMedia"]);
    expect(policy.decide(media, false)).toEqual({ kind: "fail", reason: "bufferStalledError" });
  });

  it("ignores what hls.js handles itself", () => {
    expect(new StreamErrorPolicy().decide(network({ fatal: false }), false)).toEqual({ kind: "ignore" });
  });
});

describe("dash error policy", () => {
  it("gives up at once when nothing in the manifest plays, else after a few errors", () => {
    expect(new DashErrorPolicy().error(true)).toBe(true);
    const policy = new DashErrorPolicy();
    expect([1, 2, 3].map(() => policy.error(false))).toEqual([false, false, false]);
    expect(policy.error(false)).toBe(true);
  });
});

describe("start position", () => {
  it("a switch carries the old stream's position over", () => {
    expect(startPosition("b", { fromUrl: "a", position: 42, resume: true }, null, 10_000)).toEqual({ seconds: 42, from: "switch" });
  });

  it("a retry of the same stream goes back to where it failed, not the saved progress", () => {
    expect(startPosition("a", { fromUrl: "a", position: 42, resume: true }, 30_000, 10_000)).toEqual({ seconds: 30, from: "retry" });
  });

  it("otherwise the saved progress", () => {
    expect(startPosition("a", null, null, 10_000)).toEqual({ seconds: 10, from: "progress" });
    expect(startPosition("a", null, null, undefined)).toEqual({ seconds: 0, from: "progress" });
  });

  it("never lands past the end", () => {
    expect(clampToDuration(1500, 1400)).toBe(1399);
    expect(clampToDuration(10, 1400)).toBe(10);
  });
});

describe("stall verdict", () => {
  it("a slow stream is waited for", () => expect(stallVerdict("startup", true, false)).toBe("wait"));
  it("a startup that can already play is fine", () => expect(stallVerdict("startup", false, true)).toBe("healthy"));
  it("nothing at all is a dead stream", () => {
    expect(stallVerdict("startup", false, false)).toBe("fail");
    expect(stallVerdict("buffering", false, true)).toBe("fail");
  });
});
