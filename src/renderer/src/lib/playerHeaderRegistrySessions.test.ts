import { describe, expect, it } from "vitest";
import { PlayerHeaderRegistry } from "@shared/playerHeaderRegistry";

// Complements playerHeaderRegistry.test.ts with the sequences a real watch session produces:
// switching quality, following a CDN's load-balancing redirect, and leaving the player. The thing
// being protected is that playback credentials never outlive the stream they belong to, and never
// leak onto a stream they don't.
describe("PlayerHeaderRegistry over a playback session", () => {
  const HEADERS = { Referer: "https://kodikplayer.com/" };

  it("stops attaching headers once the session that owned them is unregistered", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("a", "https://p14.cdn.test/x.m3u8", HEADERS);
    expect(registry.headersFor("https://p14.cdn.test/seg1.ts")).toEqual(HEADERS);

    registry.unregister("a");
    expect(registry.headersFor("https://p14.cdn.test/seg1.ts")).toBeUndefined();
  });

  it("drops redirect-inherited origins along with their session", () => {
    const registry = new PlayerHeaderRegistry();
    registry.register("a", "https://p14.cdn.test/x.m3u8", HEADERS);
    registry.followRedirect("https://p14.cdn.test/x.m3u8", "https://p13.cdn.test/x.m3u8");
    expect(registry.headersFor("https://p13.cdn.test/seg1.ts")).toEqual(HEADERS);

    // The redirect target was never registered directly, so nothing but the session's own
    // bookkeeping can clean it up - a leak here would leave a Referer attached to that CDN for
    // every later request the app makes to it.
    registry.unregister("a");
    expect(registry.headersFor("https://p13.cdn.test/seg1.ts")).toBeUndefined();
  });

  it("hands a switched stream its own headers rather than the previous one's", () => {
    const registry = new PlayerHeaderRegistry();
    const first = { Referer: "https://first.test/" };
    const second = { Referer: "https://second.test/" };
    registry.register("a", "https://shared.cdn.test/720.m3u8", first);
    registry.register("b", "https://shared.cdn.test/480.m3u8", second);

    expect(registry.headersFor("https://shared.cdn.test/480.m3u8")).toEqual(second);

    // The quality switch tears the old session down after the new one is up (VideoPlayer's effect
    // cleanup runs after the replacement effect's registration resolves), so the surviving session
    // must keep working.
    registry.unregister("a");
    expect(registry.headersFor("https://shared.cdn.test/480.m3u8")).toEqual(second);
  });

  it("keeps a session alive across gaps as long as it is still being used", () => {
    const registry = new PlayerHeaderRegistry(1000);
    const start = 10_000;
    registry.register("a", "https://p14.cdn.test/x.m3u8", HEADERS, start);
    expect(registry.headersFor("https://p14.cdn.test/x.m3u8", start + 999)).toEqual(HEADERS);
    // A sliding window: each request refreshes it, so a stream that keeps requesting never
    // expires no matter how far apart two requests fall - a video paused past the TTL and then
    // resumed must not come back with its playback headers already dropped.
    expect(registry.headersFor("https://p14.cdn.test/x.m3u8", start + 5000)).toEqual(HEADERS);
    expect(registry.headersFor("https://p14.cdn.test/x.m3u8", start + 5500)).toEqual(HEADERS);

    // But a session nothing asks about again is still reclaimed, once any other traffic sweeps.
    registry.register("b", "https://other.cdn.test/y.m3u8", HEADERS, start + 20_000);
    expect(registry.headersFor("https://p14.cdn.test/x.m3u8", start + 20_000)).toBeUndefined();
  });

  it("ignores a redirect from an origin it knows nothing about", () => {
    const registry = new PlayerHeaderRegistry();
    expect(registry.followRedirect("https://unknown.test/a", "https://other.test/b")).toBe(false);
    expect(registry.headersFor("https://other.test/b")).toBeUndefined();
  });
});
