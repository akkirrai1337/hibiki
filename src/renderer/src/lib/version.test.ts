import { describe, expect, it } from "vitest";
import { isExtensionUpdateAvailable, isExtensionVersionNewer, type UpdatableExtension } from "./version";

describe("isExtensionVersionNewer", () => {
  it("compares each component numerically, not as text", () => {
    expect(isExtensionVersionNewer("1.0.10", "1.0.9")).toBe(true);
    expect(isExtensionVersionNewer("1.0.9", "1.0.10")).toBe(false);
    expect(isExtensionVersionNewer("1.2.0", "1.10.0")).toBe(false);
  });

  it("treats missing components as zero", () => {
    expect(isExtensionVersionNewer("1.1", "1.0.9")).toBe(true);
    expect(isExtensionVersionNewer("1.0", "1.0.0")).toBe(false);
  });

  it("never prompts an update for an unparseable version", () => {
    expect(isExtensionVersionNewer("nightly", "1.0.0")).toBe(false);
    expect(isExtensionVersionNewer("1.0.1", "")).toBe(false);
    // Typed as string, but these come from manifest JSON - a manifest with no "version" field
    // reaches here as undefined and used to throw, blanking the Sources screen.
    expect(isExtensionVersionNewer("1.0.1", undefined as unknown as string)).toBe(false);
    expect(isExtensionVersionNewer(undefined as unknown as string, "1.0.0")).toBe(false);
  });
});

describe("isExtensionUpdateAvailable", () => {
  const KODIK: UpdatableExtension = { id: "kodik", version: "1.0.3", type: "player-resolver" };
  const SOURCE: UpdatableExtension = {
    id: "yummy-anime",
    version: "1.2.0",
    type: "source",
    resolverDependencies: ["kodik", "sibnet"],
  };
  const all = [SOURCE, KODIK];

  it("reports an update when the source itself is newer", () => {
    const installed = new Map([["yummy-anime", "1.1.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, { kodik: "1.0.3" }, all)).toBe(true);
  });

  // The case this whole function exists for: a resolver-only fix is published, the source's own
  // version is untouched, and nothing would otherwise ever offer the user that fix.
  it("reports an update when only a resolver dependency is newer", () => {
    const installed = new Map([["yummy-anime", "1.2.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, { kodik: "1.0.2" }, all)).toBe(true);
  });

  it("stays quiet when the source and every available resolver are current", () => {
    const installed = new Map([["yummy-anime", "1.2.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, { kodik: "1.0.3" }, all)).toBe(false);
  });

  it("offers repair when a resolver dependency is missing", () => {
    const installed = new Map([["yummy-anime", "1.2.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, {}, all)).toBe(true);
  });

  it("ignores a resolver the repository no longer publishes", () => {
    const installed = new Map([["yummy-anime", "1.2.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, { kodik: "1.0.3", sibnet: "1.0.0" }, all)).toBe(false);
  });

  it("does not match a resolver id against a source of the same id", () => {
    // Guards the `type` half of the lookup: an id colliding across the two kinds must not let a
    // source's version stand in for its resolver's.
    const collidingSource: UpdatableExtension = { id: "kodik", version: "9.9.9", type: "source" };
    const installed = new Map([["yummy-anime", "1.2.0"]]);
    expect(isExtensionUpdateAvailable(SOURCE, installed, { kodik: "1.0.3" }, [SOURCE, collidingSource])).toBe(false);
  });

  it("says nothing about an extension that isn't installed", () => {
    expect(isExtensionUpdateAvailable(SOURCE, new Map(), { kodik: "1.0.2" }, all)).toBe(false);
  });
});
