// Video segments kept on disk for a day, so what was already downloaded isn't downloaded again:
// a seek back past the player's 30-second back buffer, reopening an episode, a quality switch back
// to a rendition already seen. hls.js only - a progressive MP4 is one open-ended range request.
//
// One layer in the player's hls.js loader chain, on both platforms: it sees each segment under its
// real upstream URL (Android's stream proxy rewrites the request below it, with a session id that
// changes every time), so the same segment is the same key wherever it was played from. Kept in
// IndexedDB, which both web views store on disk; least recently used segments go first past
// LIMIT_BYTES. A cache that can't be opened (storage blocked, quota) just stops caching.
import type { HlsConfig, Loader, LoaderCallbacks, LoaderConfiguration, LoaderContext, LoaderStats } from "hls.js";

export const LIMIT_BYTES = 200 * 1024 * 1024;
/** Bigger than any real segment: a whole file served as one "segment" isn't worth a cache slot. */
export const OBJECT_LIMIT_BYTES = 32 * 1024 * 1024;
/** Segment URLs carry CDN tokens that expire within hours; a day bounds what is kept for nothing. */
export const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export interface VideoCacheEntry {
  key: string;
  size: number;
  savedAt: number;
  usedAt: number;
}

/** Where the bytes live: IndexedDB in the app, memory in tests. */
export interface VideoCacheStore {
  entries(): Promise<VideoCacheEntry[]>;
  read(key: string): Promise<ArrayBuffer | null>;
  write(entry: VideoCacheEntry, data: ArrayBuffer): Promise<void>;
  touch(entry: VideoCacheEntry): Promise<void>;
  remove(keys: string[]): Promise<void>;
  clear(): Promise<void>;
}

export interface VideoCache {
  get(key: string): Promise<ArrayBuffer | null>;
  put(key: string, data: ArrayBuffer): Promise<void>;
  size(): Promise<number>;
  clear(): Promise<void>;
  /** An hls.js loader class answering media segments from the cache and saving the ones it fetches.
   * `onHit` hears each segment served from disk (not downloaded: not counted as traffic or speed). */
  hlsLoader(Base: LoaderClass, onHit?: (context: LoaderContext, bytes: number) => void): LoaderClass;
}

type LoaderClass = new (config: HlsConfig) => Loader<LoaderContext>;
type LoaderInstance = Loader<LoaderContext> & {
  stats: LoaderStats;
  load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void;
  abort(): void;
  destroy(): void;
};

/** A media segment, not a playlist or an encryption key; a byte range is part of its identity. */
export function segmentKey(context: LoaderContext): string | null {
  if (context.type !== "media-fragment" || !/^https?:/i.test(context.url)) return null;
  const range = context.rangeStart !== undefined || context.rangeEnd !== undefined ? `#${context.rangeStart ?? 0}-${context.rangeEnd ?? ""}` : "";
  return context.url + range;
}

function servedStats(bytes: number): LoaderStats {
  const now = performance.now();
  return {
    aborted: false,
    loaded: bytes,
    retry: 0,
    total: bytes,
    chunkCount: 0,
    bwEstimate: 0,
    loading: { start: now, first: now, end: now },
    parsing: { start: now, end: now },
    buffering: { start: now, first: now, end: now },
  };
}

export function createVideoCache(store: VideoCacheStore, now: () => number = Date.now): VideoCache {
  let index: Promise<Map<string, VideoCacheEntry>> | null = null;
  const loadIndex = () => {
    index ??= store.entries().then(
      (entries) => {
        const map = new Map(entries.map((entry) => [entry.key, entry]));
        const expired = entries.filter((entry) => now() - entry.savedAt > MAX_AGE_MS).map((entry) => entry.key);
        for (const key of expired) map.delete(key);
        if (expired.length) void store.remove(expired).catch(() => undefined);
        return map;
      },
      () => new Map<string, VideoCacheEntry>(),
    );
    return index;
  };
  const total = (map: Map<string, VideoCacheEntry>) => {
    let sum = 0;
    for (const entry of map.values()) sum += entry.size;
    return sum;
  };

  const cache: VideoCache = {
    async get(key) {
      const map = await loadIndex();
      const entry = map.get(key);
      if (!entry) return null;
      if (now() - entry.savedAt > MAX_AGE_MS) {
        map.delete(key);
        void store.remove([key]).catch(() => undefined);
        return null;
      }
      const data = await store.read(key).catch(() => null);
      if (!data || data.byteLength !== entry.size) {
        map.delete(key);
        void store.remove([key]).catch(() => undefined);
        return null;
      }
      entry.usedAt = now();
      void store.touch(entry).catch(() => undefined);
      return data;
    },

    async put(key, data) {
      const size = data.byteLength;
      if (size === 0 || size > OBJECT_LIMIT_BYTES) return;
      const map = await loadIndex();
      if (map.has(key)) return;
      // Least recently used first, until the new segment fits.
      let used = total(map);
      const evicted: string[] = [];
      if (used + size > LIMIT_BYTES) {
        for (const entry of [...map.values()].sort((a, b) => a.usedAt - b.usedAt)) {
          if (used + size <= LIMIT_BYTES) break;
          used -= entry.size;
          map.delete(entry.key);
          evicted.push(entry.key);
        }
      }
      if (evicted.length) await store.remove(evicted).catch(() => undefined);
      const entry = { key, size, savedAt: now(), usedAt: now() };
      map.set(key, entry);
      try {
        await store.write(entry, data);
      } catch {
        map.delete(key);
      }
    },

    async size() {
      return total(await loadIndex());
    },

    async clear() {
      (await loadIndex()).clear();
      await store.clear().catch(() => undefined);
    },

    hlsLoader(Base, onHit) {
      return class CachingLoader extends (Base as new (config: HlsConfig) => LoaderInstance) {
        private lookupCancelled = false;

        load(context: LoaderContext, config: LoaderConfiguration, callbacks: LoaderCallbacks<LoaderContext>): void {
          const key = segmentKey(context);
          if (!key) {
            super.load(context, config, callbacks);
            return;
          }
          this.lookupCancelled = false;
          const fetchAndKeep = () => {
            super.load(context, config, {
              ...callbacks,
              onSuccess: (response, stats, ctx, networkDetails) => {
                // Copied now: hls.js hands the buffer on to its transmuxer, which detaches it.
                if (response.data instanceof ArrayBuffer) void cache.put(key, response.data.slice(0));
                callbacks.onSuccess(response, stats, ctx, networkDetails);
              },
            });
          };
          void cache.get(key).then(
            (data) => {
              if (this.lookupCancelled) return;
              if (!data) {
                fetchAndKeep();
                return;
              }
              this.stats = servedStats(data.byteLength);
              onHit?.(context, data.byteLength);
              callbacks.onSuccess({ url: context.url, data }, this.stats, context, null);
            },
            () => {
              if (!this.lookupCancelled) fetchAndKeep();
            },
          );
        }

        abort(): void {
          this.lookupCancelled = true;
          super.abort();
        }

        destroy(): void {
          this.lookupCancelled = true;
          super.destroy();
        }
      } as unknown as LoaderClass;
    },
  };
  return cache;
}

const DB_NAME = "hibiki-video-cache";
const DATA = "data";
const META = "meta";

/** Two object stores: the small entries read whole at start, and the bytes read one by one. */
export function indexedDbStore(): VideoCacheStore {
  let db: Promise<IDBDatabase> | null = null;
  const open = () => {
    db ??= new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = () => {
        request.result.createObjectStore(DATA);
        request.result.createObjectStore(META, { keyPath: "key" });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    return db;
  };
  const run = async <T>(stores: string[], mode: IDBTransactionMode, work: (tx: IDBTransaction) => IDBRequest<T> | void): Promise<T | undefined> => {
    const database = await open();
    return new Promise<T | undefined>((resolve, reject) => {
      const tx = database.transaction(stores, mode);
      const request = work(tx);
      tx.oncomplete = () => resolve(request ? request.result : undefined);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  };
  return {
    entries: async () => (await run<VideoCacheEntry[]>([META], "readonly", (tx) => tx.objectStore(META).getAll())) ?? [],
    read: async (key) => ((await run<ArrayBuffer>([DATA], "readonly", (tx) => tx.objectStore(DATA).get(key))) ?? null),
    write: async (entry, data) => {
      await run([DATA, META], "readwrite", (tx) => {
        tx.objectStore(DATA).put(data, entry.key);
        tx.objectStore(META).put(entry);
      });
    },
    touch: async (entry) => {
      await run([META], "readwrite", (tx) => {
        tx.objectStore(META).put(entry);
      });
    },
    remove: async (keys) => {
      await run([DATA, META], "readwrite", (tx) => {
        for (const key of keys) {
          tx.objectStore(DATA).delete(key);
          tx.objectStore(META).delete(key);
        }
      });
    },
    clear: async () => {
      await run([DATA, META], "readwrite", (tx) => {
        tx.objectStore(DATA).clear();
        tx.objectStore(META).clear();
      });
    },
  };
}

/** The app's one cache: the player reads and fills it, Settings > Network shows and empties it. */
export const videoCache = createVideoCache(indexedDbStore());
