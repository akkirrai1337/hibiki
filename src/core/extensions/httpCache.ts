// Source responses kept on disk so a repeat request can ask "changed since?" instead of
// downloading the page again. Only responses that carry a validator (ETag / Last-Modified) are kept
// - without one there is nothing to ask with - and the next GET of the same URL and headers sends
// If-None-Match / If-Modified-Since. A 304 then costs a few hundred bytes instead of the whole body.
// Measured on the sources at hand (2026-10): anichi, anikoto, anikappa and mikai answer 304;
// most others send no validators, and for them nothing changes.
//
// A response the server marks fresh (Cache-Control max-age, capped at an hour) is served without
// asking at all. no-cache is kept but always asked about. no-store is honoured for anything sent
// with the user's cookies or credentials; on a plain public page (anichi's catalog sends no-store
// on everything, yet answers 304) it is treated as no-cache: kept, never served without asking.
//
// Everything here is best effort: a missing directory, a corrupt file, a full disk - any of it just
// means the request goes out as it would have without this file.
import { getPlatform } from "../platform";
import { logger } from "../logger";

const DIR_NAME = "http-cache";
const INDEX_FILE = "index.json";
/** The whole store; the least recently used entries go first beyond it. */
const MAX_TOTAL_BYTES = 40 * 1024 * 1024;
const MAX_ENTRY_BYTES = 2 * 1024 * 1024;
/** max-age is honoured up to this, so a server's "a day" can't hide a new episode for a day. */
const MAX_FRESH_MS = 60 * 60 * 1000;

export interface StoredResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
  etag?: string;
  lastModified?: string;
  savedAt: number;
  /** How long it may be served without asking; 0 = always ask. */
  freshMs: number;
}

interface IndexEntry {
  file: string;
  bytes: number;
  usedAt: number;
}

let dir: string | null = null;
let index: Map<string, IndexEntry> | null = null;
let loading: Promise<void> | null = null;
let indexWrite: ReturnType<typeof setTimeout> | null = null;

/** 64-bit FNV-1a over the key, as hex: a file name that doesn't leak the URL. */
function fileNameFor(key: string): string {
  let h1 = 0x811c9dc5;
  let h2 = 0xcbf29ce4;
  for (let i = 0; i < key.length; i++) {
    const c = key.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0;
    h2 = Math.imul(h2 ^ c, 0x0100019d) >>> 0;
  }
  return `${h1.toString(16).padStart(8, "0")}${h2.toString(16).padStart(8, "0")}.json`;
}

async function ready(): Promise<boolean> {
  if (index) return true;
  loading ??= (async () => {
    const platform = getPlatform();
    const base = platform.files.join(platform.paths.userData, DIR_NAME);
    await platform.files.mkdir(base);
    const loaded = new Map<string, IndexEntry>();
    try {
      const raw = JSON.parse(await platform.files.readText(platform.files.join(base, INDEX_FILE))) as Record<string, IndexEntry>;
      for (const [key, entry] of Object.entries(raw)) loaded.set(key, entry);
    } catch {
      // No index yet, or unreadable: start empty (stray files are overwritten or cleared later).
    }
    dir = base;
    index = loaded;
  })().catch(() => { loading = null; });
  await loading;
  return index !== null;
}

function scheduleIndexWrite(): void {
  if (indexWrite) return;
  indexWrite = setTimeout(() => {
    indexWrite = null;
    if (!dir || !index) return;
    const platform = getPlatform();
    void platform.files.writeText(platform.files.join(dir, INDEX_FILE), JSON.stringify(Object.fromEntries(index))).catch(() => undefined);
  }, 2000);
}

/** The stored response for `key`, or null. */
export async function readStored(key: string): Promise<StoredResponse | null> {
  try {
    if (!(await ready())) return null;
    const entry = index!.get(key);
    if (!entry) return null;
    const platform = getPlatform();
    const stored = JSON.parse(await platform.files.readText(platform.files.join(dir!, entry.file))) as StoredResponse;
    entry.usedAt = Date.now();
    scheduleIndexWrite();
    return stored;
  } catch {
    return null;
  }
}

/** Whether a stored response may be used without asking the server. */
export function isFresh(stored: StoredResponse, now = Date.now()): boolean {
  return stored.freshMs > 0 && now - stored.savedAt < stored.freshMs;
}

function header(headers: Record<string, string>, name: string): string | undefined {
  const wanted = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) if (key.toLowerCase() === wanted) return value;
  return undefined;
}

/** What Cache-Control allows: null = don't keep; otherwise how long it is fresh (ms). */
export function cachePolicy(headers: Record<string, string>, personal = true): number | null {
  const cc = (header(headers, "cache-control") ?? "").toLowerCase();
  // With no cookies or credentials sent (`personal` false), no-store counts as no-cache.
  if (/\bno-store\b/.test(cc)) return personal ? null : 0;
  if (/\bno-cache\b/.test(cc)) return 0;
  const maxAge = /\bmax-age=(\d+)/.exec(cc);
  if (!maxAge) return 0;
  const age = Number(header(headers, "age") ?? 0) || 0;
  return Math.max(0, Math.min(MAX_FRESH_MS, (Number(maxAge[1]) - age) * 1000));
}

/** Keeps a 200 response that has a validator (or a freshness lifetime), within the size limits. */
export async function storeResponse(
  key: string,
  response: { status: number; headers: Record<string, string>; body: string },
  requestHeaders: Record<string, string> = {},
): Promise<void> {
  try {
    if (response.status !== 200 || response.body.length > MAX_ENTRY_BYTES) return;
    const personal = header(requestHeaders, "cookie") !== undefined || header(requestHeaders, "authorization") !== undefined;
    const freshMs = cachePolicy(response.headers, personal);
    if (freshMs === null) return;
    const etag = header(response.headers, "etag");
    const lastModified = header(response.headers, "last-modified");
    if (!etag && !lastModified && freshMs === 0) return;
    if (!(await ready())) return;
    // A session cookie the site set has no business sitting in a cache file.
    const headers = Object.fromEntries(Object.entries(response.headers).filter(([name]) => name.toLowerCase() !== "set-cookie"));
    const stored: StoredResponse = { status: 200, headers, body: response.body, etag, lastModified, savedAt: Date.now(), freshMs };
    const text = JSON.stringify(stored);
    const file = fileNameFor(key);
    const platform = getPlatform();
    await platform.files.writeText(platform.files.join(dir!, file), text);
    index!.set(key, { file, bytes: text.length * 2, usedAt: Date.now() });
    await evict();
    scheduleIndexWrite();
  } catch {
    // Not kept: the next request simply downloads again.
  }
}

/** A 304 said the stored copy is current: kept again with whatever caching headers the 304
 * brought (a new ETag, Cache-Control), so its freshness starts over. */
export async function refreshStored(
  key: string,
  stored: StoredResponse,
  notModifiedHeaders: Record<string, string>,
  requestHeaders: Record<string, string>,
): Promise<void> {
  const merged = { ...stored.headers };
  for (const name of ["cache-control", "etag", "last-modified", "expires", "age"]) {
    const value = header(notModifiedHeaders, name);
    if (value === undefined) continue;
    for (const key of Object.keys(merged)) if (key.toLowerCase() === name) delete merged[key];
    merged[name] = value;
  }
  await storeResponse(key, { status: 200, headers: merged, body: stored.body }, requestHeaders);
}

async function evict(): Promise<void> {
  let total = 0;
  for (const entry of index!.values()) total += entry.bytes;
  if (total <= MAX_TOTAL_BYTES) return;
  const platform = getPlatform();
  const oldestFirst = [...index!.entries()].sort((a, b) => a[1].usedAt - b[1].usedAt);
  for (const [key, entry] of oldestFirst) {
    if (total <= MAX_TOTAL_BYTES * 0.8) break;
    index!.delete(key);
    total -= entry.bytes;
    await platform.files.remove(platform.files.join(dir!, entry.file)).catch(() => undefined);
  }
  logger.debug("net", `http cache trimmed to ${Math.round(total / 1024)} KB`);
}

/** Bytes the store takes on disk (roughly: UTF-16 length of what was written). */
export async function httpCacheSize(): Promise<number> {
  if (!(await ready().catch(() => false))) return 0;
  let total = 0;
  for (const entry of index!.values()) total += entry.bytes;
  return total;
}

/** Empties the store (Settings > Network > Cache). */
export async function clearHttpCache(): Promise<void> {
  try {
    if (!(await ready())) return;
    const platform = getPlatform();
    await platform.files.remove(dir!);
    await platform.files.mkdir(dir!);
    index = new Map();
  } catch {
    // Nothing to clear.
  }
}

/** Conditional headers for a stored response, unless the caller already asked conditionally. */
export function withValidators(headers: Record<string, string>, stored: StoredResponse): Record<string, string> {
  if (Object.keys(headers).some((key) => /^if-/i.test(key))) return headers;
  const result = { ...headers };
  if (stored.etag) result["If-None-Match"] = stored.etag;
  if (stored.lastModified) result["If-Modified-Since"] = stored.lastModified;
  return result;
}
