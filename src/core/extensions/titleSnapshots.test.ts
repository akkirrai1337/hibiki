import { describe, expect, it } from "vitest";
import { LINKS_VERIFY_MS, LIST_VERIFY_MS, MAX_AGE_MS, requestKey, UNVERSIONED_MAX_AGE_MS, usable, versionOf, type TitleSnapshot } from "./titleSnapshots";

const NOW = 1_800_000_000_000;
const snapshot = (partial: Partial<TitleSnapshot>): TitleSnapshot => ({ version: "27|28||ongoing", savedAt: NOW, verifiedAt: NOW, responses: {}, ...partial });

describe("versionOf", () => {
  it("is built from what says a new episode came out", () => {
    expect(versionOf({ availableEpisodeCount: 27, episodeCount: 28, nextEpisodeAt: null, status: "ongoing" })).toBe("27|28||ongoing");
    expect(versionOf({ availableEpisodeCount: 28, episodeCount: 28, nextEpisodeAt: null, status: "ongoing" })).not.toBe("27|28||ongoing");
  });

  it("is null when the details say none of it", () => {
    expect(versionOf({})).toBeNull();
    expect(versionOf(null)).toBeNull();
  });
});

describe("requestKey", () => {
  it("does not depend on header order or case", () => {
    expect(requestKey("https://a/b", { Referer: "x", Accept: "y" })).toBe(requestKey("https://a/b", { accept: "y", referer: "x" }));
    expect(requestKey("https://a/b", { Lang: "ru" })).not.toBe(requestKey("https://a/b", { Lang: "en" }));
  });
});

describe("usable", () => {
  it("serves a list checked a moment ago, and checks the details again after a while", () => {
    expect(usable(snapshot({}), "list", undefined, NOW + 1000)).toBe("yes");
    expect(usable(snapshot({}), "list", undefined, NOW + LIST_VERIFY_MS + 1)).toBe("verify");
  });

  it("lets the player use it for as long as the title page keeps its details", () => {
    expect(usable(snapshot({}), "links", undefined, NOW + LIST_VERIFY_MS + 1)).toBe("yes");
    expect(usable(snapshot({}), "links", undefined, NOW + LINKS_VERIFY_MS + 1)).toBe("verify");
  });

  it("drops it as soon as the details read since say something changed", () => {
    expect(usable(snapshot({}), "links", "28|28||ongoing", NOW + 1000)).toBe("no");
    expect(usable(snapshot({}), "links", "27|28||ongoing", NOW + 1000)).toBe("yes");
  });

  it("never keeps one past a day, and a source without counts only half an hour", () => {
    expect(usable(snapshot({ verifiedAt: NOW + MAX_AGE_MS }), "list", undefined, NOW + MAX_AGE_MS + 1)).toBe("no");
    expect(usable(snapshot({ version: null }), "links", undefined, NOW + UNVERSIONED_MAX_AGE_MS - 1)).toBe("yes");
    expect(usable(snapshot({ version: null }), "links", undefined, NOW + UNVERSIONED_MAX_AGE_MS + 1)).toBe("no");
  });
});
