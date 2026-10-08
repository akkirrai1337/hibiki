import { describe, expect, it } from "vitest";
import { isNewerVersion, parseVersion } from "@shared/appVersion";

describe("release tag parsing", () => {
  it("accepts the shapes this project's tags actually take", () => {
    expect(parseVersion("v1.2.0")).toEqual({ numbers: [1, 2, 0], prerelease: "" });
    expect(parseVersion("1.2.0")).toEqual({ numbers: [1, 2, 0], prerelease: "" });
    expect(parseVersion("v1.0.0-prerelease.3")).toEqual({ numbers: [1, 0, 0], prerelease: "prerelease.3" });
    expect(parseVersion("1.6")).toEqual({ numbers: [1, 6], prerelease: "" });
  });

  it("rejects what it can't make sense of instead of guessing", () => {
    expect(parseVersion("nightly")).toBeNull();
    expect(parseVersion("")).toBeNull();
    expect(parseVersion("v")).toBeNull();
  });
});

describe("isNewerVersion", () => {
  it("compares numerically, not as text", () => {
    expect(isNewerVersion("1.0.10", "1.0.9")).toBe(true);
    expect(isNewerVersion("1.0.9", "1.0.10")).toBe(false);
    expect(isNewerVersion("1.10.0", "1.9.0")).toBe(true);
  });

  it("treats a missing component as zero", () => {
    expect(isNewerVersion("1.1", "1.0.9")).toBe(true);
    expect(isNewerVersion("1.0", "1.0.0")).toBe(false);
  });

  // This app publishes prereleases under the base version (see the release workflow), so without
  // the semver rule that a prerelease precedes its release, an installed 1.1.0 would be offered
  // 1.1.0-prerelease.1 as an "update" and quietly downgrade itself.
  it("never offers a prerelease as newer than the release it leads to", () => {
    expect(isNewerVersion("1.1.0-prerelease.1", "1.1.0")).toBe(false);
    expect(isNewerVersion("1.1.0", "1.1.0-prerelease.1")).toBe(true);
    expect(isNewerVersion("1.1.0-prerelease.2", "1.0.0")).toBe(true);
  });

  it("is not fooled by the leading v on one side only", () => {
    expect(isNewerVersion("v1.0.1", "1.0.0")).toBe(true);
    expect(isNewerVersion("v1.0.0", "1.0.0")).toBe(false);
  });

  it("says no rather than yes when either side is unparseable", () => {
    // A malformed tag must not be able to trigger a download.
    expect(isNewerVersion("nightly", "1.0.0")).toBe(false);
    expect(isNewerVersion("1.0.1", "nightly")).toBe(false);
  });
});
