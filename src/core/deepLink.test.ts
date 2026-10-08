import { describe, expect, it } from "vitest";
import { buildWatchDeepLink, findDeepLinkInArgv, parseWatchDeepLink } from "./deepLink";

describe("buildWatchDeepLink / parseWatchDeepLink", () => {
  it("round-trips a target through build and parse", () => {
    const target = { sourceId: "yummy-anime", animeId: "12345", groupId: "sub-group", episodeId: "ep-1" };
    expect(parseWatchDeepLink(buildWatchDeepLink(target))).toEqual(target);
  });

  it("encodes ids that contain reserved URL characters", () => {
    const target = { sourceId: "a/b", animeId: "c?d", groupId: "e f", episodeId: "g#h" };
    const link = buildWatchDeepLink(target);
    expect(link).not.toContain("a/b");
    expect(parseWatchDeepLink(link)).toEqual(target);
  });

  it("rejects a link with the wrong scheme", () => {
    expect(parseWatchDeepLink("https://watch/a/b/c/d")).toBeNull();
  });

  it("rejects a link with the wrong host", () => {
    expect(parseWatchDeepLink("hibiki://open/a/b/c/d")).toBeNull();
  });

  it("rejects a link missing a segment", () => {
    expect(parseWatchDeepLink("hibiki://watch/a/b/c")).toBeNull();
  });

  it("rejects garbage input", () => {
    expect(parseWatchDeepLink("not a url")).toBeNull();
  });
});

describe("findDeepLinkInArgv", () => {
  it("finds a hibiki:// entry among ordinary argv", () => {
    const argv = ["/usr/bin/hibiki", "--flag", "hibiki://watch/a/b/c/d"];
    expect(findDeepLinkInArgv(argv)).toBe("hibiki://watch/a/b/c/d");
  });

  it("returns null when there is none", () => {
    expect(findDeepLinkInArgv(["/usr/bin/hibiki", "--flag"])).toBeNull();
  });
});
