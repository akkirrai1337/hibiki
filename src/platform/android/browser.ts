// BrowserPort on Android: hidden WebViews (HibikiBrowser plugin). A challenge page that does not pass
// by itself within a few seconds is shown to the user, the way the Kotlin app does it - Cloudflare's
// interactive checks need a person. One challenge per origin at a time, so two calls racing for the
// same site share one page instead of tearing each other's down.
import type { BrowserFetchResult, BrowserPort, ChallengeSession, HarvestedCookie } from "../types";
import { HibikiBrowser } from "./native";
import { disposeResolverPages, performBrowserResolve } from "./browserResolve";

const SHOW_AFTER_MS = 8_000;
const GIVE_UP_AFTER_MS = 120_000;
const POLL_MS = 600;

// Same markers desktop's browserFetchHost.ts probes for: a "still challenged?" test, since the app
// cannot know what a source's real page looks like but does know what an interstitial looks like.
const CHALLENGE_PROBE_SCRIPT = `
  (function () {
    var title = (document.title || "").toLowerCase();
    var challenged = /just a moment|attention required|checking your browser|verifying you are human|один момент/.test(title)
      || !!document.querySelector("#challenge-running, #challenge-form, #cf-chl-widget, .cf-browser-verification, #turnstile-wrapper");
    return { challenged: challenged, ready: document.readyState === "complete" };
  })();
`;

function parseCookies(header: string): Array<[string, string]> {
  return header
    .split(";")
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const index = part.indexOf("=");
      return index > 0 ? ([part.slice(0, index), part.slice(index + 1)] as [string, string]) : null;
    })
    .filter((pair): pair is [string, string] => pair !== null);
}

const challengesByOrigin = new Map<string, Promise<ChallengeSession>>();

async function solveChallenge(url: string, cookieNames: string[], forceRefresh: boolean): Promise<ChallengeSession> {
  const key = `challenge:${new URL(url).origin}`;
  await HibikiBrowser.open({ key, url, clearOrigin: forceRefresh });
  try {
    const startedAt = Date.now();
    let shown = false;
    for (;;) {
      await new Promise((resolve) => setTimeout(resolve, POLL_MS));
      let state = { challenged: true, ready: false };
      try {
        state = JSON.parse((await HibikiBrowser.eval({ key, js: CHALLENGE_PROBE_SCRIPT })).value || "null") ?? state;
      } catch {
        // Navigating between the interstitial and the real page: progress, keep waiting.
      }
      if (state.ready && !state.challenged) break;
      const elapsed = Date.now() - startedAt;
      if (!shown && elapsed > SHOW_AFTER_MS) {
        shown = true;
        await HibikiBrowser.show({ key });
      }
      if (elapsed > GIVE_UP_AFTER_MS) throw new Error(`challenge for ${url} was not passed`);
    }
    const userAgent = (await HibikiBrowser.userAgent()).value;
    const all = parseCookies((await HibikiBrowser.cookies({ url })).value);
    const wanted = cookieNames.length > 0 ? all.filter(([name]) => cookieNames.includes(name)) : all;
    return {
      cookies: Object.fromEntries(wanted),
      cookieHeader: wanted.map(([name, value]) => `${name}=${value}`).join("; "),
      userAgent,
    };
  } finally {
    await HibikiBrowser.close({ key }).catch(() => {});
  }
}

// --- browserFetch: a request made from inside a loaded page --------------------------------------
// Mirrors desktop's browserFetchHost.ts: one page per pageUrl, kept for a while and reused (a
// search, a title and its episode list arrive together, and reloading the page for each is what
// trips a site's rate limiter); the request runs inside it with its cookies and fingerprint; 429/444
// are retried on the same backoff.
const PAGE_TTL_MS = 5 * 60_000;
const SETTLE_MIN_MS = 250;
const SETTLE_MAX_MS = 4_000;
const SETTLE_POLL_MS = 150;
const FETCH_TIMEOUT_MS = 12_000;
const FETCH_TOTAL_TIMEOUT_MS = 25_000;
const RATE_LIMIT_RETRY_DELAYS_MS = [1000, 2500, 5000];
const RETRYABLE_STATUSES = new Set([429, 444]);

const pages = new Map<string, { key: string; loadedAt: number; ready: Promise<void>; timer: ReturnType<typeof setTimeout> }>();
let nextFetchId = 0;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function probe(key: string): Promise<{ challenged: boolean; ready: boolean } | null> {
  try {
    return JSON.parse((await HibikiBrowser.eval({ key, js: CHALLENGE_PROBE_SCRIPT })).value || "null");
  } catch {
    return null;
  }
}

async function loadPage(key: string, url: string): Promise<void> {
  await HibikiBrowser.open({ key, url });
  const startedAt = Date.now();
  await sleep(SETTLE_MIN_MS);
  while (Date.now() - startedAt < SETTLE_MAX_MS) {
    const state = await probe(key);
    if (state && !state.challenged && state.ready) return;
    await sleep(SETTLE_POLL_MS);
  }
}

function acquirePage(pageUrl: string): Promise<string> {
  const existing = pages.get(pageUrl);
  if (existing && Date.now() - existing.loadedAt < PAGE_TTL_MS) return existing.ready.then(() => existing.key);
  if (existing) {
    clearTimeout(existing.timer);
    void HibikiBrowser.close({ key: existing.key });
  }
  const key = `page:${pageUrl}`;
  const ready = loadPage(key, pageUrl);
  const entry = {
    key,
    loadedAt: Date.now(),
    ready,
    timer: setTimeout(() => {
      if (pages.get(pageUrl) === entry) pages.delete(pageUrl);
      void HibikiBrowser.close({ key });
    }, PAGE_TTL_MS),
  };
  pages.set(pageUrl, entry);
  ready.catch(() => {
    if (pages.get(pageUrl) === entry) pages.delete(pageUrl);
  });
  return ready.then(() => key);
}

async function fetchInPage(key: string, targetUrl: string, init: Record<string, unknown>, timeoutMs: number): Promise<BrowserFetchResult & { __error?: string }> {
  // evaluateJavascript does not wait for promises, so the page parks the answer in a slot that is
  // then polled.
  const id = `f${Date.now().toString(36)}${(nextFetchId++).toString(36)}`;
  await HibikiBrowser.eval({
    key,
    js: `(function () {
      var slots = window.__hibikiFetch || (window.__hibikiFetch = {});
      var controller = new AbortController();
      var timer = setTimeout(function () { controller.abort(); }, ${timeoutMs});
      var options = ${JSON.stringify(init)};
      options.signal = controller.signal;
      fetch(${JSON.stringify(targetUrl)}, options)
        .then(async function (res) { slots[${JSON.stringify(id)}] = { status: res.status, ok: res.ok, body: await res.text(), headers: Object.fromEntries(res.headers.entries()) }; })
        .catch(function (error) { slots[${JSON.stringify(id)}] = { status: 0, ok: false, body: "", headers: {}, __error: String(error) }; })
        .finally(function () { clearTimeout(timer); });
      return true;
    })();`,
  });
  const deadline = Date.now() + timeoutMs + 1_000;
  while (Date.now() < deadline) {
    await sleep(120);
    const raw = (await HibikiBrowser.eval({
      key,
      js: `(function () { var s = window.__hibikiFetch; var r = s && s[${JSON.stringify(id)}]; if (r) delete s[${JSON.stringify(id)}]; return r ? JSON.stringify(r) : null; })();`,
    })).value;
    const parsed = JSON.parse(raw || "null") as string | null;
    if (parsed) return JSON.parse(parsed);
  }
  throw new Error(`browserFetch() timed out for ${targetUrl}`);
}

async function browserFetch(pageUrl: string, targetUrl: string, options?: { method?: string; headers?: Record<string, string>; body?: string }): Promise<BrowserFetchResult> {
  const deadline = Date.now() + FETCH_TOTAL_TIMEOUT_MS;
  const init = { method: options?.method ?? "GET", headers: options?.headers ?? {}, body: options?.body, credentials: "include" };
  for (let attempt = 0; ; attempt++) {
    const key = await acquirePage(pageUrl);
    const timeoutMs = Math.min(FETCH_TIMEOUT_MS, deadline - Date.now());
    if (timeoutMs <= 0) throw new Error(`browserFetch() timed out for ${targetUrl}`);
    const result = await fetchInPage(key, targetUrl, init, timeoutMs);
    if (result.__error) throw new Error(`browserFetch() in-page request failed: ${result.__error}`);
    if (!RETRYABLE_STATUSES.has(result.status) || attempt >= RATE_LIMIT_RETRY_DELAYS_MS.length) return result;
    const retryDelay = RATE_LIMIT_RETRY_DELAYS_MS[attempt];
    if (Date.now() + retryDelay >= deadline) throw new Error(`browserFetch() retry budget exhausted for ${targetUrl}`);
    await sleep(retryDelay);
  }
}

// --- a check passed by the person ---------------------------------------------------------------
// The site shown in the app's browser layout (title, address, close) until its check is gone, then
// Cloudflare's own cookies and the WebView's User-Agent go back to core for the app's requests. The
// WebView's cookie store is shared, so the hidden pages and APK sources are let through as well.
const SOLVED_SETTLE_MS = 1_000;
const isCloudflareCookie = (name: string) => name === "cf_clearance" || name.startsWith("__cf") || name.startsWith("cf_");

async function solveChallengeVisibly(url: string): Promise<ChallengeSession | null> {
  const key = `check:${new URL(url).origin}`;
  let closed = false;
  const listener = await HibikiBrowser.addListener("closed", (event) => {
    if (event.key === key) closed = true;
  });
  try {
    await HibikiBrowser.open({ key, url });
    await HibikiBrowser.show({ key });
    let passedAt = 0;
    for (;;) {
      await sleep(POLL_MS);
      if (closed) return null;
      const state = await probe(key);
      if (!state || state.challenged || !state.ready) {
        passedAt = 0;
        continue;
      }
      if (!passedAt) passedAt = Date.now();
      if (Date.now() - passedAt >= SOLVED_SETTLE_MS) break;
    }
    const found = new Map<string, string>();
    for (const candidate of cookieUrlsFor(url)) {
      for (const [name, value] of parseCookies((await HibikiBrowser.cookies({ url: candidate })).value)) {
        if (isCloudflareCookie(name) && !found.has(name)) found.set(name, value);
      }
    }
    // Pages kept for browserFetch may still hold the check; they load again on next use.
    for (const { key: pageKey, timer } of pages.values()) {
      clearTimeout(timer);
      void HibikiBrowser.close({ key: pageKey });
    }
    pages.clear();
    return {
      cookies: Object.fromEntries(found),
      cookieHeader: [...found].map(([name, value]) => `${name}=${value}`).join("; "),
      userAgent: (await HibikiBrowser.userAgent()).value,
    };
  } finally {
    await listener.remove();
    if (!closed) await HibikiBrowser.close({ key }).catch(() => {});
  }
}

// --- web sign-in ------------------------------------------------------------------------------------
// The site's real sign-in page, shown to the user; done once the session cookie appears, cancelled
// by Back (desktop: closing the window). CookieManager only answers per URL, so the page's own
// address and each parent domain are asked - a session cookie often lands on the bare domain.
const LOGIN_POLL_MS = 700;
const openLogins = new Set<string>();

function cookieUrlsFor(url: string): string[] {
  const { protocol, hostname } = new URL(url);
  const labels = hostname.split(".");
  const urls = [url];
  for (let i = 1; i < labels.length - 1; i++) urls.push(`${protocol}//${labels.slice(i).join(".")}/`);
  return urls;
}

async function login(sourceId: string, url: string, successCookieName: string): Promise<HarvestedCookie[]> {
  if (openLogins.has(sourceId)) throw new Error("A sign-in window for this source is already open");
  if (!URL.canParse(url)) throw new Error(`Source "${sourceId}" declares an invalid web login URL`);
  const key = `login:${sourceId}`;
  openLogins.add(sourceId);
  let closed = false;
  const listener = await HibikiBrowser.addListener("closed", (event) => {
    if (event.key === key) closed = true;
  });
  try {
    await HibikiBrowser.open({ key, url });
    await HibikiBrowser.show({ key });
    for (;;) {
      await sleep(LOGIN_POLL_MS);
      if (closed) throw new Error("Sign-in window closed");
      const found = new Map<string, string>();
      for (const candidate of cookieUrlsFor(url)) {
        for (const [name, value] of parseCookies((await HibikiBrowser.cookies({ url: candidate })).value)) {
          if (!found.has(name)) found.set(name, value);
        }
      }
      if (found.has(successCookieName)) return [...found].map(([name, value]) => ({ name, value }));
    }
  } finally {
    openLogins.delete(sourceId);
    await listener.remove();
    if (!closed) await HibikiBrowser.close({ key }).catch(() => {});
  }
}


export const androidBrowser: BrowserPort = {
  challenge(url, cookieNames, forceRefresh) {
    const origin = new URL(url).origin;
    const running = challengesByOrigin.get(origin);
    if (running && !forceRefresh) return running;
    const pending = solveChallenge(url, cookieNames, forceRefresh).finally(() => {
      if (challengesByOrigin.get(origin) === pending) challengesByOrigin.delete(origin);
    });
    challengesByOrigin.set(origin, pending);
    return pending;
  },
  browserFetch,
  solveChallenge: solveChallengeVisibly,
  resolve: performBrowserResolve,
  login,
  dispose() {
    for (const { key, timer } of pages.values()) {
      clearTimeout(timer);
      void HibikiBrowser.close({ key });
    }
    pages.clear();
    disposeResolverPages();
  },
};
