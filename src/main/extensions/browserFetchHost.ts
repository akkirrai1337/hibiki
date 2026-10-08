// Real implementations of challenge()/browserFetch() (see browserBridge.ts), using a hidden,
// real-Chromium BrowserWindow - the actual GUI-capable Electron APIs these need only exist on
// Electron's main thread, so this file must only ever be called from there (runtime.ts, in
// response to a bridge request forwarded from the extension's worker_thread - see
// syncHostBridge.ts for the worker side of that bridge).
import { BrowserWindow } from "electron";
import type { BrowserFetchResult, ChallengeSession } from "./browserBridge";

// Cloudflare-style interstitials run a few seconds of JS (proof-of-work / redirect chain) before
// the real page or cookies are ready - loadURL's own promise resolves once the *interstitial*
// finishes loading, not once it's done solving itself. So the page is polled for the interstitial's
// own markers instead, and the wait ends as soon as they are gone; this is the ceiling on that
// wait, not the wait itself. Most sources put up no interstitial at all and used to pay the full
// four seconds anyway, once per pooled window.
const CHALLENGE_SETTLE_MAX_MS = 4000;
const CHALLENGE_POLL_MS = 150;
// Even an unchallenged page gets this much: a site that sets its session cookie from its own JS
// has not necessarily done so by the time readyState reaches "complete".
const CHALLENGE_SETTLE_MIN_MS = 250;
const NAVIGATION_TIMEOUT_MS = 15_000;
const FETCH_TIMEOUT_MS = 12_000;
const FETCH_TOTAL_TIMEOUT_MS = 25_000;

// One search/title/episode-list screen makes several independent search()/getById()/
// playbackGroups() calls back-to-back, and each used to spin up its own fresh hidden window and
// reload the page from scratch - several full page loads within a couple seconds is exactly the
// kind of burst that trips a site's own rate limiter (seen live: Miruro starting to answer with
// HTTP 429 once search+getById+playbackGroups landed almost simultaneously). Pooling one window
// per page origin and reusing it (skipping the reload + settle wait entirely once it's already
// loaded) cuts that down to one real page load per session instead of one per call.
const WINDOW_IDLE_TTL_MS = 5 * 60_000;
const pool = new Map<string, { window: BrowserWindow; loadedAt: number }>();
const pendingLoads = new Map<string, Promise<BrowserWindow>>();

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  if (timeoutMs <= 0) throw new Error(message);
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => reject(new Error(message)), timeoutMs);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

// Markers of an interstitial still running, rather than of the real page: title text Cloudflare
// and its lookalikes use, plus the containers their widget mounts into. Deliberately a "still
// challenged?" test and not a "real page?" test - the app cannot know what any given source's real
// page looks like, but it does know what a challenge looks like.
export const CHALLENGE_PROBE_SCRIPT = `
  (function () {
    var title = (document.title || "").toLowerCase();
    var challenged = /just a moment|attention required|checking your browser|verifying you are human|один момент/.test(title)
      || !!document.querySelector("#challenge-running, #challenge-form, #cf-chl-widget, .cf-browser-verification, #turnstile-wrapper");
    return { challenged: challenged, ready: document.readyState === "complete" };
  })();
`;

async function waitForChallengeSettle(win: BrowserWindow): Promise<void> {
  const startedAt = Date.now();
  await new Promise((resolve) => setTimeout(resolve, CHALLENGE_SETTLE_MIN_MS));
  while (Date.now() - startedAt < CHALLENGE_SETTLE_MAX_MS) {
    if (win.isDestroyed()) return;
    let state: { challenged: boolean; ready: boolean };
    try {
      state = (await win.webContents.executeJavaScript(CHALLENGE_PROBE_SCRIPT)) as { challenged: boolean; ready: boolean };
    } catch {
      // Thrown while the interstitial is navigating to the real page - which is progress, not a
      // failure. Keep waiting.
      await new Promise((resolve) => setTimeout(resolve, CHALLENGE_POLL_MS));
      continue;
    }
    if (!state.challenged && state.ready) return;
    await new Promise((resolve) => setTimeout(resolve, CHALLENGE_POLL_MS));
  }
}

function getPooledWindow(key: string): BrowserWindow | null {
  const entry = pool.get(key);
  if (!entry) return null;
  if (entry.window.isDestroyed() || Date.now() - entry.loadedAt > WINDOW_IDLE_TTL_MS) {
    if (!entry.window.isDestroyed()) entry.window.destroy();
    pool.delete(key);
    return null;
  }
  return entry.window;
}

async function acquireLoadedWindow(key: string, url: string, forceReload: boolean): Promise<BrowserWindow> {
  const existing = !forceReload && getPooledWindow(key);
  if (existing) return existing;

  // Search, title details and playback groups are commonly requested together. Before the first
  // page load has reached the pool they must share that in-flight load too, otherwise every call
  // sees an empty pool and starts its own Chromium process/navigation (the exact burst pooling is
  // meant to prevent).
  const pending = !forceReload ? pendingLoads.get(key) : null;
  if (pending) return pending;

  const load = createLoadedWindow(key, url, forceReload);
  pendingLoads.set(key, load);
  try {
    return await load;
  } finally {
    if (pendingLoads.get(key) === load) pendingLoads.delete(key);
  }
}

async function createLoadedWindow(key: string, url: string, forceReload: boolean): Promise<BrowserWindow> {
  const previous = pool.get(key);
  if (previous && !previous.window.isDestroyed()) previous.window.destroy();

  const win = new BrowserWindow({ show: false, webPreferences: { sandbox: true, contextIsolation: true, images: false } });
  win.webContents.setAudioMuted(true); // A challenge/fetch page is never meant to be heard.
  win.on("closed", () => {
    if (pool.get(key)?.window === win) pool.delete(key);
  });
  try {
    if (forceReload) await win.webContents.session.clearStorageData({ origin: new URL(url).origin });
    await withTimeout(win.loadURL(url), NAVIGATION_TIMEOUT_MS, `Browser page navigation timed out for ${url}`);
    await waitForChallengeSettle(win);
    pool.set(key, { window: win, loadedAt: Date.now() });
    return win;
  } catch (error) {
    if (!win.isDestroyed()) win.destroy();
    throw error;
  }
}

/** Releases every pooled window - call on app quit so no hidden renderer processes linger. */
export function destroyAllPooledWindows(): void {
  for (const { window } of pool.values()) if (!window.isDestroyed()) window.destroy();
  pool.clear();
  // Pending windows are registered in the pool only after loading. They observe app shutdown via
  // Electron, while clearing the map prevents a later caller from adopting a stale promise.
  pendingLoads.clear();
}

const RATE_LIMIT_RETRY_DELAYS_MS = [1000, 2500, 5000];
// 429 is the standard "too many requests" signal; 444 is an Nginx-ism for "closing the connection
// without a response" that some CDNs reuse defensively against bursty/bot-like traffic (seen live
// on Miruro's own player-source endpoint) - both are worth a short backoff-and-retry rather than
// failing the whole request outright.
const RETRYABLE_STATUSES = new Set([429, 444]);

export async function performBrowserFetch(
  pageUrl: string,
  targetUrl: string,
  options?: { method?: string; headers?: Record<string, string>; body?: string },
): Promise<BrowserFetchResult> {
  const deadline = Date.now() + FETCH_TOTAL_TIMEOUT_MS;
  const requestInit = {
    method: options?.method ?? "GET",
    headers: options?.headers ?? {},
    body: options?.body,
    credentials: "include" as const,
  };
  for (let attempt = 0; ; attempt++) {
    const win = await acquireLoadedWindow(pageUrl, pageUrl, false);
    const requestTimeoutMs = Math.min(FETCH_TIMEOUT_MS, deadline - Date.now());
    if (requestTimeoutMs <= 0) throw new Error(`browserFetch() timed out for ${targetUrl}`);
    const script = `
      (function () {
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, ${requestTimeoutMs});
        var options = ${JSON.stringify(requestInit)};
        options.signal = controller.signal;
        return fetch(${JSON.stringify(targetUrl)}, options)
          .then(async (res) => ({
            status: res.status,
            ok: res.ok,
            body: await res.text(),
            headers: Object.fromEntries(res.headers.entries()),
          }))
          .catch((error) => ({ status: 0, ok: false, body: "", headers: {}, __error: String(error) }))
          .finally(function () { clearTimeout(timer); });
      })();
    `;
    // Runs inside the page itself, so it carries whatever cookies/session the challenge just
    // established and matches that page's own TLS/JS fingerprint - the entire point of routing
    // this through a real browser context instead of our own fetch().
    const result = await withTimeout(
      win.webContents.executeJavaScript(script),
      requestTimeoutMs,
      `browserFetch() timed out for ${targetUrl}`,
    );
    if (result && typeof result === "object" && "__error" in result) {
      throw new Error(`browserFetch() in-page request failed: ${(result as { __error: string }).__error}`);
    }
    if (!RETRYABLE_STATUSES.has(result.status) || attempt >= RATE_LIMIT_RETRY_DELAYS_MS.length) return result as BrowserFetchResult;
    const retryDelay = RATE_LIMIT_RETRY_DELAYS_MS[attempt];
    if (Date.now() + retryDelay >= deadline) throw new Error(`browserFetch() retry budget exhausted for ${targetUrl}`);
    await new Promise((resolve) => setTimeout(resolve, retryDelay));
  }
}

export async function performChallenge(url: string, cookieNames: string[], forceRefresh: boolean): Promise<ChallengeSession> {
  const origin = new URL(url).origin;
  const win = await acquireLoadedWindow(origin, url, forceRefresh);

  const userAgent = win.webContents.getUserAgent();
  const allCookies = await win.webContents.session.cookies.get({ url });
  const wanted = cookieNames.length > 0 ? allCookies.filter((c) => cookieNames.includes(c.name)) : allCookies;

  const cookies: Record<string, string> = {};
  for (const cookie of wanted) cookies[cookie.name] = cookie.value;
  const cookieHeader = wanted.map((cookie) => `${cookie.name}=${cookie.value}`).join("; ");

  return { cookies, cookieHeader, userAgent };
}
