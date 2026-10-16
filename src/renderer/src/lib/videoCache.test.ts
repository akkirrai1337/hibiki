import { describe, expect, it } from "vitest";
import type { HlsConfig, LoaderCallbacks, LoaderContext } from "hls.js";
import { createVideoCache, LIMIT_BYTES, MAX_AGE_MS, segmentKey, type VideoCacheEntry, type VideoCacheStore } from "./videoCache";

function memoryStore() {
  const meta = new Map<string, VideoCacheEntry>();
  const data = new Map<string, ArrayBuffer>();
  const store: VideoCacheStore = {
    entries: async () => [...meta.values()].map((entry) => ({ ...entry })),
    read: async (key) => data.get(key) ?? null,
    write: async (entry, bytes) => {
      meta.set(entry.key, { ...entry });
      data.set(entry.key, bytes);
    },
    touch: async (entry) => {
      meta.set(entry.key, { ...entry });
    },
    remove: async (keys) => {
      for (const key of keys) {
        meta.delete(key);
        data.delete(key);
      }
    },
    clear: async () => {
      meta.clear();
      data.clear();
    },
  };
  return { store, meta, data };
}

const segment = (url: string, extra: Partial<LoaderContext> = {}) => ({ url, type: "media-fragment", responseType: "arraybuffer", ...extra }) as LoaderContext;
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

// Stands in for hls.js's XhrLoader: counts the requests that actually reach the network.
class FakeLoader {
  static requests: string[] = [];
  stats = { loaded: 0 };
  constructor(_config: HlsConfig) {}
  load(context: LoaderContext, _config: unknown, callbacks: LoaderCallbacks<LoaderContext>) {
    FakeLoader.requests.push(context.url);
    callbacks.onSuccess({ url: context.url, data: new Uint8Array([1, 2, 3]).buffer }, this.stats as never, context, null);
  }
  abort() {}
  destroy() {}
}

function loadWith(Loader: new (config: HlsConfig) => unknown, context: LoaderContext) {
  return new Promise<{ bytes: number; stats: { loaded: number } }>((resolve, reject) => {
    const loader = new Loader({} as HlsConfig) as FakeLoader;
    loader.load(context, {}, {
      onSuccess: (response, stats) => resolve({ bytes: (response.data as ArrayBuffer).byteLength, stats: stats as never }),
      onError: () => reject(new Error("error")),
      onTimeout: () => reject(new Error("timeout")),
    });
  });
}

describe("segmentKey", () => {
  it("keys media segments by URL and byte range, nothing else", () => {
    expect(segmentKey(segment("https://cdn/a.ts"))).toBe("https://cdn/a.ts");
    expect(segmentKey(segment("https://cdn/a.mp4", { rangeStart: 0, rangeEnd: 999 }))).toBe("https://cdn/a.mp4#0-999");
    expect(segmentKey({ ...segment("https://cdn/index.m3u8"), type: "level" } as LoaderContext)).toBeNull();
    expect(segmentKey({ ...segment("https://cdn/key.bin"), type: "key" } as LoaderContext)).toBeNull();
  });
});

describe("createVideoCache", () => {
  it("gives back what was put, and forgets it after a day", async () => {
    let time = 1_000;
    const cache = createVideoCache(memoryStore().store, () => time);
    await cache.put("a", new Uint8Array([1, 2]).buffer);
    expect((await cache.get("a"))?.byteLength).toBe(2);
    time += MAX_AGE_MS + 1;
    expect(await cache.get("a")).toBeNull();
  });

  it("drops the least recently used segments to stay under the limit", async () => {
    let time = 0;
    const { store, meta } = memoryStore();
    const cache = createVideoCache(store, () => ++time);
    const piece = LIMIT_BYTES / 8;
    for (let i = 0; i < 8; i++) await cache.put(`s${i}`, new ArrayBuffer(piece));
    await cache.get("s0");
    await cache.put("new", new ArrayBuffer(piece));
    expect(meta.has("s0")).toBe(true);
    expect(meta.has("s1")).toBe(false);
    expect(meta.has("new")).toBe(true);
    expect(await cache.size()).toBe(LIMIT_BYTES);
  });

  it("serves a segment it has from disk and fetches only the rest", async () => {
    FakeLoader.requests = [];
    const hits: number[] = [];
    const cache = createVideoCache(memoryStore().store);
    const Loader = cache.hlsLoader(FakeLoader as never, (_context, bytes) => hits.push(bytes));
    await loadWith(Loader, segment("https://cdn/1.ts"));
    await flush();
    const again = await loadWith(Loader, segment("https://cdn/1.ts"));
    await loadWith(Loader, { ...segment("https://cdn/index.m3u8"), type: "level" } as LoaderContext);
    await loadWith(Loader, { ...segment("https://cdn/index.m3u8"), type: "level" } as LoaderContext);
    expect(FakeLoader.requests).toEqual(["https://cdn/1.ts", "https://cdn/index.m3u8", "https://cdn/index.m3u8"]);
    expect(again.bytes).toBe(3);
    expect(again.stats.loaded).toBe(3);
    expect(hits).toEqual([3]);
  });

  it("keeps working without a usable store", async () => {
    FakeLoader.requests = [];
    const broken: VideoCacheStore = {
      entries: () => Promise.reject(new Error("blocked")),
      read: () => Promise.reject(new Error("blocked")),
      write: () => Promise.reject(new Error("blocked")),
      touch: () => Promise.reject(new Error("blocked")),
      remove: () => Promise.reject(new Error("blocked")),
      clear: () => Promise.reject(new Error("blocked")),
    };
    const Loader = createVideoCache(broken).hlsLoader(FakeLoader as never);
    await loadWith(Loader, segment("https://cdn/1.ts"));
    await flush();
    await loadWith(Loader, segment("https://cdn/1.ts"));
    expect(FakeLoader.requests).toEqual(["https://cdn/1.ts", "https://cdn/1.ts"]);
  });
});
