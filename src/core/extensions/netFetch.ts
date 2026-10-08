// Host-side HTTP for extension scripts' synchronous `fetch()` global (see globals.ts), reached
// from the worker over the synchronous bridge (syncHostBridge.ts on desktop). The transport itself
// is platform.http - Node's fetch on desktop, a native client on Android - and everything that
// decides how a request is made (default headers, retries, the short GET cache, batching) lives
// here, so both platforms treat extension traffic the same way.
//
// What this replaces, and why: `fetch()` in an extension has to *return* its response, not a
// Promise - the scripts are ports of Rhino code and are written straight through. That was
// implemented with the `sync-fetch` package, which gets its synchrony by spawning a whole child
// process of `process.execPath` per request and blocking on execFileSync. Inside Electron
// `process.execPath` is the Electron binary, so every single HTTP request an extension made paid
// for a fresh ~90ms Electron/Node boot (measured on this machine, warm; a cold or AV-scanned start
// is far worse) - and, being a new process each time, could never reuse a TCP connection or a TLS
// session. Resolving one Kodik embed makes three requests to the same origin: three process
// spawns, three TCP handshakes, three TLS handshakes, for data a single pooled connection serves.
//
// Worse than slow, it could hang forever: sync-fetch passes no timeout to execFileSync and none to
// node-fetch inside the child, so a stalled connect (routine for Kodik's domains on some Russian
// ISPs - the very case this app hits) blocked the worker until runtime.ts's 30s kill, with no way
// to tell that apart from a genuine failure. Everything below runs on the main thread's normal
// async stack instead, where undici's global pool keeps connections alive across requests and an
// AbortSignal gives every request a real deadline.
import { logger } from "../logger";
import { getPlatform } from "../platform";
import { forgetClearance, isCloudflareChallenge, withClearance } from "./cloudflare";

export interface NetFetchOptions {
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface NetFetchResult {
  status: number;
  ok: boolean;
  body: string;
  headers: Record<string, string>;
  /** Only set by performNetFetchAll, for a request that never produced a response. */
  error?: string;
}

/** A request that hasn't produced *any* response by now is treated as dead. Chosen to sit
 * comfortably under runtime.ts's 30s per-call worker timeout, so a single slow request surfaces as
 * "this URL timed out" (retryable, and logged as such) rather than killing the whole extension
 * call with no indication of which request was to blame. */
const REQUEST_TIMEOUT_MS = 12_000;
/** One retry, and only for transport-level failures (connect reset/timeout) - never for an HTTP
 * status, which is the server's real answer. Kodik's edge nodes drop connections intermittently;
 * a single immediate retry turns most of those into a normal load instead of a failed episode. */
const RETRY_ATTEMPTS = 2;
const RETRY_BACKOFF_MS = 250;

/** Short-lived GET response cache. Two things in this app's own hot path fetch an identical URL
 * within a second or two of each other: yummy-anime.js reads /anime/{id}/videos in both
 * getPlaybackGroups() and getPlayerLinks(), and kodik.js re-downloads the same 144KB
 * app.player_single script on *every* mirror it resolves. Neither can cache it itself - each call
 * runs in a fresh worker with a fresh VM context - so the cache has to live out here, on the one
 * thread they all funnel through. Deliberately tiny and short: this is request coalescing, not a
 * content cache, and playback URLs must never be served stale. */
const CACHE_TTL_MS = 60_000;
const CACHE_MAX_ENTRIES = 64;
const CACHE_MAX_BODY_BYTES = 2 * 1024 * 1024;

// undici identifies itself as "undici" by default (node-fetch, which sync-fetch wrapped, said
// "node-fetch"). Neither looks like a browser, and several of the providers this app talks to -
// Kodik's edge among them - vary what they serve, or whether they answer at all, by User-Agent;
// "it plays fine on the website" is the user-visible shape of exactly that difference. Applied
// only as a default, never over a header an extension set for itself, since a few sources
// deliberately send their own (hentaimama pins one, anikappa reuses the challenge session's, and
// a solved Cloudflare cookie is bound to the UA it was solved with - overriding those would break
// the session outright).
const DEFAULT_HEADERS: Record<string, string> = {
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
};

function withDefaultHeaders(headers: Record<string, string>): Record<string, string> {
  const present = new Set(Object.keys(headers).map((key) => key.toLowerCase()));
  const result = { ...headers };
  for (const [key, value] of Object.entries(DEFAULT_HEADERS)) {
    if (!present.has(key.toLowerCase())) result[key] = value;
  }
  return result;
}

interface CacheEntry {
  expiresAt: number;
  result: NetFetchResult;
}

const cache = new Map<string, CacheEntry>();
/** In-flight de-duplication, separate from the TTL cache above: several resolvers running
 * back-to-back ask for the same script before the first answer has landed, and without this each
 * would open its own connection for a body the others are already downloading. */
const inFlight = new Map<string, Promise<NetFetchResult>>();

function cacheKey(url: string, headers: Record<string, string>): string {
  // Any request header may participate in Vary or application-level routing. In particular,
  // Authorization and Lang distinguish accounts/locales in current sources; keying only on
  // Referer/Cookie could serve a cached profile or translated payload to the wrong call.
  const normalizedHeaders = Object.entries(headers)
    .map(([key, value]) => [key.toLowerCase(), value] as const)
    .sort(([a], [b]) => a.localeCompare(b));
  return `${url}\n${JSON.stringify(normalizedHeaders)}`;
}

function readCache(key: string): NetFetchResult | null {
  const entry = cache.get(key);
  if (!entry) return null;
  if (entry.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }
  return entry.result;
}

function writeCache(key: string, result: NetFetchResult): void {
  if (!result.ok || result.body.length > CACHE_MAX_BODY_BYTES) return;
  if (cache.size >= CACHE_MAX_ENTRIES) {
    const oldest = cache.keys().next();
    if (!oldest.done) cache.delete(oldest.value);
  }
  cache.set(key, { expiresAt: Date.now() + CACHE_TTL_MS, result });
}

function isRetryable(error: unknown): boolean {
  const name = error instanceof Error ? error.name : "";
  const message = error instanceof Error ? error.message : String(error);
  if (name === "TimeoutError" || name === "AbortError") return true;
  return /ECONNRESET|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|ENOTFOUND|socket hang up|fetch failed|network/i.test(message);
}

async function performOnce(url: string, options: NetFetchOptions, headers: Record<string, string>): Promise<NetFetchResult> {
  const response = await getPlatform().http.request({
    url,
    method: (options.method ?? "GET").toUpperCase(),
    headers,
    body: options.body,
    redirect: "follow",
    timeoutMs: REQUEST_TIMEOUT_MS,
  });
  // One string per header, as extensions have always seen them. node-fetch (what sync-fetch used)
  // exposed multiple Set-Cookie headers joined by ", "; the transport keeps them apart, and
  // kodik.js reads headers["set-cookie"] and keeps everything up to the first ";", so it works
  // either way - but joining them back keeps any extension that looks at more than the first
  // cookie seeing the same shape it did before.
  const responseHeaders: Record<string, string> = {};
  for (const [name, values] of Object.entries(response.headers)) responseHeaders[name] = values.join(", ");
  return { status: response.status, ok: response.status >= 200 && response.status < 300, body: response.body, headers: responseHeaders };
}

async function fetchUncached(url: string, options: NetFetchOptions, headers: Record<string, string>): Promise<NetFetchResult> {
  const method = (options.method ?? "GET").toUpperCase();
  let lastError: unknown;
  for (let attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
    const startedAt = Date.now();
    try {
      const result = await performOnce(url, options, headers);
      logger.debug("net", `${method} ${url} -> ${result.status} in ${Date.now() - startedAt}ms (${result.body.length}b)${attempt > 1 ? ` [attempt ${attempt}]` : ""}`);
      return result;
    } catch (error) {
      lastError = error;
      const reason = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      const retrying = attempt < RETRY_ATTEMPTS && isRetryable(error);
      logger.warn("net", `${method} ${url} failed after ${Date.now() - startedAt}ms - ${reason}${retrying ? " (retrying)" : ""}`);
      if (!retrying) break;
      await new Promise((resolve) => setTimeout(resolve, RETRY_BACKOFF_MS));
    }
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

export async function performNetFetch(url: string, options: NetFetchOptions = {}): Promise<NetFetchResult> {
  // A site whose Cloudflare check was passed in the app's window gets that clearance on every request.
  const requested = withDefaultHeaders(options.headers ?? {});
  const cleared = withClearance(url, requested);
  const headers = cleared ?? requested;
  const method = (options.method ?? "GET").toUpperCase();
  const cacheable = method === "GET";
  const key = cacheKey(url, headers);

  if (cacheable) {
    const cached = readCache(key);
    if (cached) {
      logger.debug("net", `GET ${url} -> ${cached.status} (cached)`);
      return cached;
    }
    const pending = inFlight.get(key);
    if (pending) {
      logger.debug("net", `GET ${url} -> joining in-flight request`);
      return pending;
    }
  }

  const request = fetchUncached(url, options, headers).then((result) => {
    if (cleared && isCloudflareChallenge(result.status, result.headers, result.body)) forgetClearance(url);
    if (cacheable) writeCache(key, result);
    return result;
  });

  if (!cacheable) return request;
  inFlight.set(key, request);
  try {
    return await request;
  } finally {
    inFlight.delete(key);
  }
}

/** How many of a batch actually run at once. An extension asking for a page's worth of detail
 * requests should get them concurrently, but it shouldn't be able to open fifty sockets to one
 * host either - past a handful the server starts queueing them anyway, and the only thing the
 * extra concurrency buys is a worse chance of being rate-limited. */
const MAX_BATCH_CONCURRENCY = 8;

/**
 * Several requests at once, in the order they were asked for.
 *
 * This exists because extension scripts are synchronous by design - `fetch()` returns a response
 * rather than a promise, matching the Rhino runtime they were written against - which means every
 * request an extension makes is strictly serial even when the requests have nothing to do with
 * each other. yummy-anime's getById asks for a title, its trailers and its recommendations; those
 * three are independent, and running them one after another measured 467ms against 236ms for the
 * same three in parallel.
 *
 * A failure is reported per request rather than sinking the batch: getting two of three answers
 * is strictly better than getting none, and the caller can decide what a missing one means. That
 * is the one place this deliberately differs from `fetch`, which throws.
 */
export async function performNetFetchAll(
  requests: Array<{ url: string; options?: NetFetchOptions }>,
): Promise<NetFetchResult[]> {
  const results = new Array<NetFetchResult>(requests.length);
  let next = 0;

  async function worker(): Promise<void> {
    for (;;) {
      const index = next++;
      if (index >= requests.length) return;
      const { url, options } = requests[index];
      try {
        results[index] = await performNetFetch(url, options ?? {});
      } catch (error) {
        // Shaped like a response so a script can branch on `.ok` without a second code path, with
        // status 0 for "never got an answer" - the same thing XHR reports for a failure below HTTP.
        results[index] = {
          status: 0,
          ok: false,
          body: "",
          headers: {},
          error: error instanceof Error ? error.message : String(error),
        };
      }
    }
  }

  const startedAt = Date.now();
  await Promise.all(Array.from({ length: Math.min(MAX_BATCH_CONCURRENCY, requests.length) }, worker));
  const failed = results.filter((r) => !r.ok).length;
  logger.debug("net", `batch of ${requests.length} finished in ${Date.now() - startedAt}ms${failed ? ` (${failed} failed)` : ""}`);
  return results;
}

/** Drops the coalescing cache. Called when the user explicitly asks for fresh data (reinstalling a
 * source, retrying a failed episode) so a cached error page can't outlive the thing that fixed it. */
export function clearNetFetchCache(): void {
  cache.clear();
}
