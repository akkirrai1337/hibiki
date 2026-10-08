// Real-Chromium equivalents of the Android app's WebView-backed `challenge()`/`browserFetch()`
// globals (see RhinoExtensionRuntime.kt ChallengeFunction/BrowserFetchFunction), for sources
// that sit behind a Cloudflare-style interactive challenge or bind a solved challenge to the
// exact client's TLS/JS fingerprint.
//
// NOT YET IMPLEMENTED: extensions currently run synchronously via node:vm on the main thread
// (see runtime.ts), so calling into async BrowserWindow APIs from inside a script would deadlock
// the app. Doing this properly needs the extension to run off-thread (a worker_thread) with a
// SharedArrayBuffer + Atomics.wait bridge back to this (real Electron main-thread-only) API —
// tracked as follow-up work. Until then, sources that call challenge()/browserFetch() (anikappa,
// animepahe, miruro, kickassanime-player, anitube-player, animepahe-player, alloha) will throw
// here instead of silently returning wrong data.
export interface ChallengeSession {
  cookies: Record<string, string>;
  cookieHeader: string;
  userAgent: string;
}

export interface ChallengeProvider {
  acquire(url: string, cookieNames: string[], forceRefresh: boolean): ChallengeSession;
}

export interface BrowserFetchResult {
  status: number;
  ok: boolean;
  body: string;
  headers: Record<string, string>;
}

/** Plain HTTP for extension scripts' `fetch()` global. Implemented on the main thread (see
 * core/extensions/netFetch.ts) and reached synchronously from the worker over the Atomics bridge - the
 * fallback below is what runs when there is no host to bridge to (unit tests, tooling). */
export interface NetFetchRequest {
  url: string;
  options?: { method?: string; headers?: Record<string, string>; body?: string };
}

export interface NetFetchProvider {
  fetch(
    url: string,
    options?: { method?: string; headers?: Record<string, string>; body?: string },
  ): BrowserFetchResult;
  /** All of them at once, answers in the order asked. One bridge round trip for the batch - doing
   * it per request would give back most of what the concurrency wins. */
  fetchAll(requests: NetFetchRequest[]): BrowserFetchResult[];
}

export interface BrowserFetchProvider {
  fetch(
    pageUrl: string,
    targetUrl: string,
    options?: { method?: string; headers?: Record<string, string>; body?: string },
  ): BrowserFetchResult;
}

export const notImplementedChallengeProvider: ChallengeProvider = {
  acquire() {
    throw new Error(
      "challenge() is not implemented yet on desktop — this source needs a browser-backed challenge session.",
    );
  },
};

export const notImplementedBrowserFetchProvider: BrowserFetchProvider = {
  fetch() {
    throw new Error(
      "browserFetch() is not implemented yet on desktop — this source needs a browser-backed fetch.",
    );
  },
};
