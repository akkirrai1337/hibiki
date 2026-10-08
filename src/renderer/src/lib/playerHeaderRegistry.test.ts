import { describe, expect, it } from "vitest";
import { PlayerHeaderRegistry } from "@shared/playerHeaderRegistry";

describe("PlayerHeaderRegistry", () => {
  it("carries headers through a cross-origin redirect", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("playback-1", "https://p12.cdn.test/video/master.m3u8", { Referer: "https://player.test/embed" }, 0);

    expect(registry.followRedirect(
      "https://p12.cdn.test/video/master.m3u8",
      "https://p13.cdn.test/video/master.m3u8",
      1,
    )).toBe(true);
    expect(registry.headersFor("https://p13.cdn.test/video/segment-1.ts", 2)).toEqual({
      Referer: "https://player.test/embed",
    });
  });

  it("shares a playback session with a subtitle origin", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("playback-1", "https://video.test/master.m3u8", { Referer: "https://player.test" }, 0);

    expect(registry.registerOrigin("playback-1", "https://subs.test/episode-1.vtt", 1)).toBe(true);
    expect(registry.headersFor("https://subs.test/episode-1.vtt", 2)).toEqual({ Referer: "https://player.test" });
  });

  it("removes every redirect origin when playback ends", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("playback-1", "https://p12.cdn.test/master.m3u8", { Authorization: "token" }, 0);
    registry.followRedirect("https://p12.cdn.test/master.m3u8", "https://p13.cdn.test/master.m3u8", 1);

    registry.unregister("playback-1");

    expect(registry.headersFor("https://p12.cdn.test/segment.ts", 2)).toBeUndefined();
    expect(registry.headersFor("https://p13.cdn.test/segment.ts", 2)).toBeUndefined();
  });

  it("keeps the newest playback isolated when two sessions share a CDN", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("old", "https://cdn.test/old.m3u8", { Referer: "https://old.test" }, 0);
    registry.register("current", "https://cdn.test/current.m3u8", { Referer: "https://current.test" }, 1);

    expect(registry.headersFor("https://cdn.test/segment.ts", 2)).toEqual({ Referer: "https://current.test" });
    registry.unregister("current");
    expect(registry.headersFor("https://cdn.test/segment.ts", 3)).toEqual({ Referer: "https://old.test" });
  });

  it("expires abandoned sessions", () => {
    const registry = new PlayerHeaderRegistry(100);
    registry.register("playback-1", "https://cdn.test/master.m3u8", { Cookie: "secret" }, 0);

    expect(registry.headersFor("https://cdn.test/segment.ts", 99)).toEqual({ Cookie: "secret" });

    // Abandoned means nothing asks for it again - so the sweep has to be driven by *other*
    // traffic. Asserting it through a request to the same origin (as this test first did) tested
    // the opposite thing: that request is exactly the evidence the session is still in use, and
    // expiring on it meant a video paused longer than the TTL resumed without its headers. See
    // playerHeaderRegistrySessions.test.ts for the sliding-window half of this.
    registry.register("playback-2", "https://other.test/master.m3u8", { Cookie: "other" }, 500);
    expect(registry.headersFor("https://cdn.test/segment.ts", 500)).toBeUndefined();
  });
});

describe("PlayerHeaderRegistry.playbackHeaders", () => {
  it("serves the first-registered session for an origin nobody registered", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("stream", "https://fetch.example/master.m3u8", { Referer: "https://embed.example/" }, 1000);
    registry.register("sub", "https://subs.example/en.vtt", { Referer: "https://subs.example/" }, 2000);
    expect(registry.headersFor("https://k9uq6.segments.example/seg-0.jpg", 3000)).toBeUndefined();
    expect(registry.playbackHeaders(3000)).toEqual({ Referer: "https://embed.example/" });
  });

  it("has nothing to serve once every session is gone", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("stream", "https://fetch.example/master.m3u8", { Referer: "https://embed.example/" }, 1000);
    registry.unregister("stream");
    expect(registry.playbackHeaders(2000)).toBeUndefined();
  });
});
