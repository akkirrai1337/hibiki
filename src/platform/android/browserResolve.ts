// BrowserPort.resolve on Android: runs a BROWSER-runtime resolver's page script inside the embed and
// collects the streams it reports - a port of desktop's main/extensions/browserResolveHost.ts with
// the same timings, early exits, validation and header assembly, so a resolver finds the same
// streams on both platforms. The difference is only in how the script reaches the embed frame: see
// HibikiResolverPlugin.java (document-start scripts + a message channel per frame, since Android
// WebView cannot evaluate inside a child frame the way Electron's WebFrameMain can).
import type { PlayerLink } from "@shared/types";
import { subtitleTracksFrom, type ReportedSubtitle } from "@shared/resolverSubtitles";
import { logger } from "../../core/logger";
import type { ResolvedStream } from "../types";
import { HibikiResolver } from "./native";

const FIRST_PROBE_DELAY_MS = 100;
const PROBE_DELAY_MS = 500;
const MAX_PROBES = 30;
const TIMEOUT_MS = 25_000;
const NAVIGATION_TIMEOUT_MS = 10_000;
const VALIDATION_TIMEOUT_MS = 5_000;
const SETTLE_MS = 600;
const MAX_VALIDATION_CONCURRENCY = 3;

const PLAYLIST_URL_PATTERN = /\.m3u8(\?|#|$)/i;
const PLAYLIST_HEAD_BYTES = 2048;
const MASTER_PLAYLIST_REQUEST_PATTERN = /\/master(?:[-_.][^/?#]*)?\.m3u8(?:[?#]|$)/i;
const PLACEHOLDER_URL_PATTERN = /cdn\.plyr\.io\/static\/blank\.mp4/i;
const REPLAYABLE_HEADER_DENYLIST = new Set([
  "host", "connection", "content-length", "accept-encoding", "range", "if-range", "if-none-match", "if-modified-since",
  "if-match", "if-unmodified-since", "cookie", "te", "trailer", "transfer-encoding", "upgrade", "priority", "content-type",
]);

type CaptureKind = "master" | "video" | "audio" | "stream" | "network";
interface Capture {
  url: string;
  kind: CaptureKind;
  quality: string | null;
  /** The audio track it carries, when the resolver reported several (HibikiResolver.track). */
  track?: string | null;
}
interface PageState {
  done: boolean;
  captures: Capture[];
  lastQuality: string | null;
  subtitles?: ReportedSubtitle[];
}
interface StreamProbe {
  reachable: boolean;
  head: string;
}
const CAPTURE_KIND_RANK: Record<CaptureKind, number> = { master: 0, video: 1, audio: 2, stream: 3, network: 4 };

// Installed in every frame at document start. The HibikiResolver bridge and the <video> watcher are
// desktop's BRIDGE_SCRIPT; media elements are muted (desktop mutes the whole hidden window, WebView
// has no such switch); HibikiHost is the WebMessageListener object native code injects.
const BOOT_SCRIPT = `
(function () {
  if (window.__hibikiBoot) return;
  window.__hibikiBoot = true;
  try {
    var play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () { try { this.muted = true; this.volume = 0; } catch (e) {} return play.apply(this, arguments); };
  } catch (e) {}
  document.addEventListener("play", function (e) { try { e.target.muted = true; } catch (x) {} }, true);
  function installBridge() {
    window.__hibikiDone = false;
    window.__hibikiCaptures = window.__hibikiCaptures || [];
    window.__hibikiLastQuality = null;
    window.__hibikiLastTrack = null;
    window.HibikiResolver = {
      quality: function (label) { window.__hibikiLastQuality = label == null ? null : String(label); },
      // One of several streams of the same episode that differ only in sound (Alloha's audio tracks).
      track: function (label) { window.__hibikiLastTrack = label == null ? null : String(label); },
      master: function (url) { window.__hibikiCaptures.push({ kind: "master", url: String(url), quality: window.__hibikiLastQuality, track: window.__hibikiLastTrack }); },
      video: function (url) { window.__hibikiCaptures.push({ kind: "video", url: String(url), quality: window.__hibikiLastQuality, track: window.__hibikiLastTrack }); },
      audio: function (url) { window.__hibikiCaptures.push({ kind: "audio", url: String(url), quality: window.__hibikiLastQuality, track: window.__hibikiLastTrack }); },
      stream: function (url) { window.__hibikiCaptures.push({ kind: "stream", url: String(url), quality: window.__hibikiLastQuality, track: window.__hibikiLastTrack }); },
      subtitle: function (url, label, language) {
        window.__hibikiSubtitles = window.__hibikiSubtitles || [];
        window.__hibikiSubtitles.push({ url: url == null ? null : String(url), label: label == null ? null : String(label), language: language == null ? null : String(language) });
      },
      done: function () { window.__hibikiDone = true; },
    };
  }
  installBridge();
  (function watchVideoElement() {
    var seen = null;
    function check() {
      var v = document.querySelector("video");
      if (v && v.currentSrc && v.currentSrc !== seen && /\\.m3u8(\\?|#|$)/i.test(v.currentSrc)) {
        seen = v.currentSrc;
        window.HibikiResolver.stream(v.currentSrc);
      }
    }
    document.addEventListener("loadedmetadata", check, true);
    document.addEventListener("canplay", check, true);
    document.addEventListener("playing", check, true);
    setInterval(check, 1000);
  })();
  function probe(url, range, wantsHead, timeoutMs) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, timeoutMs);
    return fetch(url, { method: "GET", headers: { Range: range }, signal: controller.signal })
      .then(function (r) {
        if (!wantsHead) return { ok: r.ok, status: r.status, head: "" };
        return r.text().then(function (text) { return { ok: r.ok, status: r.status, head: String(text).slice(0, ${PLAYLIST_HEAD_BYTES}) }; });
      })
      .catch(function () { return { ok: false, status: 0, head: "" }; })
      .finally(function () { clearTimeout(timer); });
  }
  var host = window.HibikiHost;
  if (!host) return;
  host.onmessage = function (event) {
    var msg;
    try { msg = JSON.parse(event.data); } catch (e) { return; }
    function reply(ok, value) {
      try { host.postMessage(JSON.stringify({ type: "result", id: msg.id, ok: ok, value: value === undefined ? null : value })); }
      catch (e) { host.postMessage(JSON.stringify({ type: "result", id: msg.id, ok: false, value: String(e) })); }
    }
    try {
      var value;
      if (msg.action === "runResolver") {
        // Desktop re-runs its bridge script with every injection: same reset here.
        installBridge();
        value = typeof window.__hibikiResolverFn === "function" ? window.__hibikiResolverFn() : (0, eval)(msg.code);
      } else if (msg.action === "readState") {
        value = { done: window.__hibikiDone === true, captures: window.__hibikiCaptures || [], lastQuality: window.__hibikiLastQuality || null, subtitles: window.__hibikiSubtitles || [] };
      } else if (msg.action === "probe") {
        value = probe(msg.url, msg.range, msg.wantsHead, msg.timeoutMs);
      }
      Promise.resolve(value).then(function (v) { reply(true, v); }, function (e) { reply(false, String(e)); });
    } catch (e) {
      reply(false, String(e));
    }
  };
  host.postMessage(JSON.stringify({ type: "hello", url: location.href }));
})();
`;

/** The resolver's page script as a function returning its completion value - every BROWSER
 * resolver's script is one invoked function expression, which is what its "no-player" travels in. */
function resolverDefinition(script: string): string {
  const expression = script.trim().replace(/;+\s*$/, "");
  return `window.__hibikiResolverFn = function () { return (\n${expression}\n); };`;
}

function resolverUrlLabel(raw: string): string {
  try {
    const url = new URL(raw);
    const tail = url.pathname.split("/").filter(Boolean).slice(-2).map((part) => (part.length > 24 ? `${part.slice(0, 8)}…` : part)).join("/");
    return `${url.host}/${tail || "…"}${url.search ? "?…" : ""}`;
  } catch {
    return "<invalid-url>";
  }
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  if (timeoutMs <= 0) throw new Error(message);
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([promise, new Promise<never>((_resolve, reject) => (timer = setTimeout(() => reject(new Error(message)), timeoutMs)))]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

function streamTypeForUrl(url: string): string {
  const clean = url.split("?")[0].split("#")[0].toLowerCase();
  if (clean.endsWith(".m3u8")) return "HLS";
  if (clean.endsWith(".mpd")) return "DASH";
  return "MP4";
}

let nextPageId = 0;
let nextRequestId = 0;

class ResolverPage {
  constructor(readonly key = `resolve:${Date.now().toString(36)}${(nextPageId++).toString(36)}`) {}

  async frames(): Promise<Array<{ id: number; url: string; origin: string; main: boolean }>> {
    return (await HibikiResolver.frames({ key: this.key })).frames;
  }

  async lastFrameId(): Promise<number> {
    return (await this.frames()).reduce((max, frame) => Math.max(max, frame.id), 0);
  }

  /** The first frame announced after `afterId` that `match` accepts, or null when none comes in time. */
  async waitFrame(afterId: number, match: (frame: { main: boolean; url: string }) => boolean, timeoutMs: number): Promise<number | null> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const frame = (await this.frames()).find((candidate) => candidate.id > afterId && match(candidate));
      if (frame) return frame.id;
      await sleep(50);
    }
    return null;
  }

  async call<T>(frameId: number, message: Record<string, unknown>, timeoutMs: number, what: string): Promise<T> {
    const id = `r${Date.now().toString(36)}${(nextRequestId++).toString(36)}`;
    const reply = await withTimeout(
      HibikiResolver.frameCall({ key: this.key, frameId, id, payload: JSON.stringify({ ...message, id }) }),
      timeoutMs,
      what,
    );
    const parsed = JSON.parse(reply.raw) as { ok: boolean; value: unknown };
    if (!parsed.ok) throw new Error(String(parsed.value));
    return parsed.value as T;
  }

  async evalTop(js: string): Promise<{ value: unknown; url: string | null }> {
    const result = await HibikiResolver.evalTop({ key: this.key, js });
    return { value: JSON.parse(result.value || "null"), url: result.url ?? null };
  }
}

// Every resolve used to start a fresh WebView - a whole renderer per embed, paid again for each mirror
// the fallback chain tries. As on desktop, pages are kept for a while and handed to the next resolve,
// remembered *together with the referring page they show*, so a second episode from the same source
// skips that page load and only injects a new iframe.
const IDLE_TTL_MS = 5 * 60_000;
const RESET_TIMEOUT_MS = 2_000;
const MAX_IDLE_PAGES = 2;

interface IdlePage {
  page: ResolverPage;
  /** The referring page loaded in it, or null for a page holding nothing reusable. */
  refererUrl: string | null;
  timer: ReturnType<typeof setTimeout>;
}

const idlePages: IdlePage[] = [];

function takeIdlePage(match: (entry: IdlePage) => boolean): IdlePage | null {
  for (let i = idlePages.length - 1; i >= 0; i--) {
    const entry = idlePages[i];
    if (!match(entry)) continue;
    idlePages.splice(i, 1);
    clearTimeout(entry.timer);
    return entry;
  }
  return null;
}

/** A page ready for this resolver's script, plus whether it already holds `refererUrl`. */
async function acquirePage(refererUrl: string | null, script: string): Promise<{ page: ResolverPage; hasReferer: boolean }> {
  const reusable = (refererUrl ? takeIdlePage((entry) => entry.refererUrl === refererUrl) : null) ?? takeIdlePage(() => true);
  if (reusable) {
    try {
      await HibikiResolver.setResolverScript({ key: reusable.page.key, resolverScript: resolverDefinition(script) });
      return { page: reusable.page, hasReferer: refererUrl !== null && reusable.refererUrl === refererUrl };
    } catch {
      await HibikiResolver.close({ key: reusable.page.key }).catch(() => {});
    }
  }
  const page = new ResolverPage();
  await HibikiResolver.open({ key: page.key, bootScript: BOOT_SCRIPT, resolverScript: resolverDefinition(script) });
  return { page, hasReferer: false };
}

async function releasePage(page: ResolverPage, refererUrl: string | null): Promise<void> {
  if (idlePages.length >= MAX_IDLE_PAGES) {
    await HibikiResolver.close({ key: page.key }).catch(() => {});
    return;
  }
  try {
    // An embed that broke out of its frame has navigated the page somewhere else entirely; what is
    // showing decides what this page may be pooled as, not what it was asked to load.
    const showing = (await withTimeout(page.evalTop("location.href"), RESET_TIMEOUT_MS, "reset timed out")).url;
    const held = refererUrl && showing && originOf(showing) === originOf(refererUrl) ? refererUrl : null;
    if (held) {
      // The embed goes now, not at the next resolve: an idle page must not keep somebody's player
      // loading and playing in it.
      await withTimeout(page.evalTop(`(function () { if (document.body) document.body.innerHTML = ""; return true; })();`), RESET_TIMEOUT_MS, "reset timed out");
    } else {
      await HibikiResolver.navigate({ key: page.key, url: "about:blank" });
    }
    const entry: IdlePage = {
      page,
      refererUrl: held,
      timer: setTimeout(() => {
        const index = idlePages.indexOf(entry);
        if (index >= 0) idlePages.splice(index, 1);
        void HibikiResolver.close({ key: page.key }).catch(() => {});
      }, IDLE_TTL_MS),
    };
    idlePages.push(entry);
  } catch {
    await HibikiResolver.close({ key: page.key }).catch(() => {});
  }
}

/** Releases every idle resolver page. */
export function disposeResolverPages(): void {
  for (const entry of idlePages.splice(0)) {
    clearTimeout(entry.timer);
    void HibikiResolver.close({ key: entry.page.key }).catch(() => {});
  }
}

export async function performBrowserResolve(link: PlayerLink, script: string, timeoutMs = TIMEOUT_MS, parentUrl?: string | null): Promise<ResolvedStream[]> {
  const deadline = Date.now() + Math.min(TIMEOUT_MS, timeoutMs);
  const resolveStartedAt = Date.now();
  let refererUrl: string | null = link.headers?.Referer ?? link.headers?.referer ?? null;
  try {
    if (parentUrl) {
      const parsed = new URL(parentUrl);
      if (parsed.protocol === "http:" || parsed.protocol === "https:") refererUrl = parsed.href;
    }
  } catch {
    // Keep the source-provided referrer for malformed/non-HTTP resolver context URLs.
  }

  const { page, hasReferer } = await acquirePage(refererUrl, script);
  logger.info("resolve", `browser pipeline started for ${link.playerName ?? "?"}: target=${resolverUrlLabel(link.url)}, window=${hasReferer ? "pooled with referer" : "navigation required"}`);
  // The referring page this page may be pooled under afterwards: set only once the embed is actually
  // running as a child frame of it.
  let heldRefererUrl: string | null = null;
  const networkCaptures: Capture[] = [];
  const capturedRequestHeaders = new Map<string, Record<string, string>>();
  let currentQuality: string | null = null;

  const collectNetwork = async () => {
    for (const capture of (await HibikiResolver.takeCaptures({ key: page.key })).captures) {
      networkCaptures.push({ url: capture.url, kind: "network", quality: currentQuality });
      capturedRequestHeaders.set(capture.url, capture.headers);
    }
  };

  // Everything below the top-level document is cancelled while the referring page loads: it is only
  // wanted as an origin to host the iframe from, and its body is replaced right after.
  const loadRefererDocument = async (url: string): Promise<boolean> => {
    const startedAt = Date.now();
    const navDeadline = Math.min(deadline, Date.now() + NAVIGATION_TIMEOUT_MS);
    const before = await page.lastFrameId();
    await HibikiResolver.navigate({ key: page.key, url, documentOnly: true });
    try {
      // WebView reports the new URL as soon as loading starts, while evaluateJavascript still runs
      // in the previous document - so first wait for the new document itself to announce itself.
      const committed = await page.waitFrame(before, (candidate) => candidate.main && originOf(candidate.url) === originOf(url), Math.max(0, navDeadline - Date.now()));
      while (committed !== null && Date.now() < navDeadline) {
        const { value } = await page.evalTop("document.readyState");
        if (value === "complete") {
          logger.debug("resolve", `browser referer document ready in ${Date.now() - startedAt}ms: ${resolverUrlLabel(url)}`);
          return true;
        }
        await sleep(100);
      }
      const current = (await page.evalTop("location.href")).url;
      const usable = current !== null && originOf(current) === originOf(url);
      logger.warn("resolve", `browser referer navigation failed in ${Date.now() - startedAt}ms; usable=${usable}: ${resolverUrlLabel(url)}; timed out`);
      return usable;
    } finally {
      await HibikiResolver.setDocumentOnly({ key: page.key, value: false });
    }
  };

  const loadTopLevel = async (): Promise<number> => {
    const before = await page.lastFrameId();
    await HibikiResolver.navigate({ key: page.key, url: link.url, headers: link.headers ?? {} });
    const frame = await page.waitFrame(before, (candidate) => candidate.main && /^https?:/i.test(candidate.url), Math.max(0, Math.min(NAVIGATION_TIMEOUT_MS, deadline - Date.now())));
    if (frame === null) throw new Error(`Navigation timed out for ${link.url}`);
    return frame;
  };

  try {
    let target: number;
    const refererStartedAt = Date.now();
    const refererReady = refererUrl ? hasReferer || (await loadRefererDocument(refererUrl)) : false;
    const refererMs = Date.now() - refererStartedAt;
    if (refererUrl && refererReady) {
      // A real iframe on the referring page, not a top-level load: the target server sees a
      // cross-site embed (Sec-Fetch-Site/Dest), which some players (Alloha) insist on.
      const before = await page.lastFrameId();
      await page.evalTop(`
        (function () {
          var iframe = document.createElement("iframe");
          iframe.src = ${JSON.stringify(link.url)};
          iframe.referrerPolicy = "unsafe-url";
          iframe.style.cssText = "position:fixed;inset:0;width:100%;height:100%;border:0;";
          iframe.setAttribute("allow", "autoplay");
          if (!document.body) document.documentElement.appendChild(document.createElement("body"));
          document.body.innerHTML = "";
          document.body.appendChild(iframe);
          return true;
        })();
      `);
      const embedStartedAt = Date.now();
      // A new iframe first holds an about:blank document, and the boot script announces that one
      // too; the embed is the first child frame that has committed a real page (desktop waits for
      // the frame's own navigation for the same reason).
      const child = await page.waitFrame(before, (candidate) => !candidate.main && /^https?:/i.test(candidate.url), Math.max(0, Math.min(8_000, deadline - Date.now())));
      logger.debug("resolve", `embed ready: referer ${refererMs}ms${hasReferer ? " (pooled)" : ""}, frame ${Date.now() - embedStartedAt}ms; target=${resolverUrlLabel(link.url)}`);
      if (child !== null) {
        target = child;
        heldRefererUrl = refererUrl;
      } else {
        target = await loadTopLevel();
      }
    } else {
      target = await loadTopLevel();
    }

    const runResolver = () => page.call<unknown>(target, { action: "runResolver", code: script }, deadline - Date.now(), "Browser resolver script timed out");
    const readPageState = async (): Promise<PageState> => {
      try {
        return await page.call<PageState>(target, { action: "readState" }, deadline - Date.now(), "Browser resolver state probe timed out");
      } catch {
        return { done: false, captures: [], lastQuality: null };
      }
    };

    let started = false;
    let done = false;
    let lastCount = 0;
    const reportedSubtitles: ReportedSubtitle[] = [];
    // Every stream gets the tracks the page reported, whichever probe it was found on.
    const withSubtitles = (streams: ResolvedStream[]): ResolvedStream[] => {
      const subtitles = subtitleTracksFrom(reportedSubtitles, link.url, streams[0]?.headers["User-Agent"]);
      if (subtitles.length > 0) logger.info("resolve", `browser resolver reported ${subtitles.length} subtitle track(s): ${subtitles.map((track) => track.label).join(", ")}`);
      return subtitles.length > 0 ? streams.map((stream) => ({ ...stream, subtitles })) : streams;
    };
    let lastChangeAt = Date.now();
    const probes = new Map<string, Promise<StreamProbe>>();
    for (let probe = 0; probe < MAX_PROBES && !done && Date.now() < deadline; probe++) {
      if (!started) {
        let result: unknown;
        const scriptStartedAt = Date.now();
        try {
          result = await runResolver();
        } catch (error) {
          logger.warn("resolve", `browser extractor script execution failed after ${Date.now() - scriptStartedAt}ms: ${resolverUrlLabel(link.url)}; ${String(error)}`);
          result = "no-player";
        }
        started = result !== "no-player";
        logger.debug("resolve", `browser extractor script executed in ${Date.now() - scriptStartedAt}ms; ready=${started}; target=${resolverUrlLabel(link.url)}`);
      }

      const probeDelay = Math.min(PROBE_DELAY_MS, FIRST_PROBE_DELAY_MS * 2 ** probe);
      await sleep(Math.min(probeDelay, Math.max(0, deadline - Date.now())));
      const probeStartedAt = Date.now();
      // Tagged with the quality from the previous probe, as desktop tags them at request time.
      await collectNetwork();
      const state = await readPageState();
      currentQuality = state.lastQuality;
      reportedSubtitles.push(...(state.subtitles ?? []));
      done = state.done;
      if (probe < 8 || done || state.captures.length > 0 || networkCaptures.length > 0) {
        logger.debug("resolve", `browser probe ${probe + 1}: state read ${Date.now() - probeStartedAt}ms; done=${done}; captured=${state.captures.length + networkCaptures.length}; quality=${state.lastQuality ?? "?"}; elapsed=${Date.now() - resolveStartedAt}ms`);
      }

      const masters = state.captures.filter((capture) => capture.kind === "master" && !PLACEHOLDER_URL_PATTERN.test(capture.url));
      if (masters.length > 0) {
        logger.debug("resolve", `master playlist(s) captured on probe ${probe + 1}: ${masters.length}; stopping early`);
        return withSubtitles(await buildResult(masters, [], link, page, target, deadline, probes, capturedRequestHeaders));
      }
      if (networkCaptures.some((capture) => MASTER_PLAYLIST_REQUEST_PATTERN.test(capture.url))) {
        logger.debug("resolve", `master playlist requested by the page on probe ${probe + 1}; stopping early`);
        break;
      }
      const totalCount = state.captures.length + networkCaptures.length + (state.subtitles?.length ?? 0);
      if (totalCount !== lastCount) {
        lastCount = totalCount;
        lastChangeAt = Date.now();
      } else if (totalCount > 0 && Date.now() - lastChangeAt > SETTLE_MS) {
        break;
      }
      if (done) return await withSubtitles(await buildResult(state.captures, networkCaptures, link, page, target, deadline, probes, capturedRequestHeaders));
    }

    await collectNetwork();
    const finalState = await readPageState();
    logger.info("resolve", `browser probing finished after ${Date.now() - resolveStartedAt}ms: page=${finalState.captures.length}, network=${networkCaptures.length}; validating candidates`);
    reportedSubtitles.push(...(finalState.subtitles ?? []));
    return withSubtitles(await buildResult(finalState.captures, networkCaptures, link, page, target, deadline, probes, capturedRequestHeaders));
  } finally {
    void releasePage(page, heldRefererUrl);
  }
}

/** One reachability check per captured URL, made from inside the frame that captured it (same
 * engine and fingerprint the stream was requested with) and cached for the whole resolve. */
async function probeStream(page: ResolverPage, target: number, url: string, deadline: number, cache: Map<string, Promise<StreamProbe>>): Promise<StreamProbe> {
  const existing = cache.get(url);
  if (existing) return existing;
  const wantsHead = PLAYLIST_URL_PATTERN.test(url);
  const range = wantsHead ? `bytes=0-${PLAYLIST_HEAD_BYTES - 1}` : "bytes=0-0";
  const probe = (async (): Promise<StreamProbe> => {
    const startedAt = Date.now();
    try {
      const timeoutMs = Math.min(VALIDATION_TIMEOUT_MS, deadline - Date.now());
      const result = await page.call<{ ok: boolean; status: number; head: string }>(
        target,
        { action: "probe", url, range, wantsHead, timeoutMs: VALIDATION_TIMEOUT_MS },
        timeoutMs,
        `Stream validation timed out for ${url}`,
      );
      const reachable = result.ok || result.status === 206;
      logger.debug("resolve", `browser candidate validation ${reachable ? "ok" : "failed"} in ${Date.now() - startedAt}ms: ${resolverUrlLabel(url)} status=${result.status}`);
      return { reachable, head: result.head ?? "" };
    } catch (error) {
      logger.warn("resolve", `browser candidate validation errored after ${Date.now() - startedAt}ms: ${resolverUrlLabel(url)}; ${String(error)}`);
      return { reachable: false, head: "" };
    }
  })();
  cache.set(url, probe);
  return probe;
}

async function buildResult(
  pageCaptures: Capture[],
  networkCaptures: Capture[],
  link: PlayerLink,
  page: ResolverPage,
  target: number,
  deadline: number,
  probes: Map<string, Promise<StreamProbe>>,
  capturedRequestHeaders: ReadonlyMap<string, Record<string, string>>,
): Promise<ResolvedStream[]> {
  const buildStartedAt = Date.now();
  const seen = new Set<string>();
  const combined = [...pageCaptures, ...networkCaptures]
    .filter((c) => !PLACEHOLDER_URL_PATTERN.test(c.url))
    .filter((c) => (seen.has(c.url) ? false : (seen.add(c.url), true)))
    .sort((a, b) => CAPTURE_KIND_RANK[a.kind] - CAPTURE_KIND_RANK[b.kind]);
  if (combined.length === 0) throw new Error("Browser resolver found no playable stream");
  logger.debug("resolve", `browser candidates collected: ${combined.length} (${combined.map((capture) => `${capture.kind}/${capture.quality ?? "?"}@${resolverUrlLabel(capture.url)}`).join(", ")})`);

  const baseHeaders: Record<string, string> = {
    ...(link.headers ?? {}),
    "User-Agent": (await HibikiResolver.userAgent()).value,
    Referer: link.url,
    Accept: "*/*",
    "Sec-Fetch-Dest": "video",
    "Sec-Fetch-Mode": "no-cors",
    "Sec-Fetch-Site": "cross-site",
  };
  try {
    baseHeaders.Origin = new URL(link.url).origin;
  } catch {
    // Leave malformed edge cases to the validation/fallback path.
  }

  // A resolver's explicit report is authoritative; only network observations are checked.
  const reachable = new Array<boolean>(combined.length).fill(false);
  let nextValidation = 0;
  async function validateWorker(): Promise<void> {
    for (;;) {
      const index = nextValidation++;
      if (index >= combined.length) return;
      if (combined[index].kind !== "network") {
        reachable[index] = true;
        continue;
      }
      reachable[index] = (await probeStream(page, target, combined[index].url, deadline, probes)).reachable;
    }
  }
  await Promise.all(Array.from({ length: Math.min(MAX_VALIDATION_CONCURRENCY, combined.length) }, validateWorker));
  const reachableCaptures = combined.filter((_capture, index) => reachable[index]);
  const selected = reachableCaptures.length > 0 ? reachableCaptures : combined;
  logger.info("resolve", `browser candidate checks finished in ${Date.now() - buildStartedAt}ms: reachable=${reachableCaptures.length}/${combined.length}; returning=${selected.length}`);
  if (reachableCaptures.length === 0) logger.warn("resolve", `same-page validation was blocked for ${combined.length} captured stream(s); returning browser captures`);

  return Promise.all(
    selected.map(async (capture): Promise<ResolvedStream> => {
      // Cookies belong to the media CDN, not to the embed page.
      const streamCookies = (await HibikiResolver.cookies({ url: capture.url })).value;
      const capturedHeaders = capturedRequestHeaders.get(capture.url);
      const headers: Record<string, string> = { ...baseHeaders, ...(capturedHeaders ?? {}) };
      for (const name of Object.keys(headers)) {
        if (REPLAYABLE_HEADER_DENYLIST.has(name.toLowerCase())) delete headers[name];
      }
      if (streamCookies) headers.Cookie = streamCookies;
      logger.debug("resolve", `captured browser headers for ${new URL(capture.url).hostname}: ${capturedHeaders ? Object.keys(capturedHeaders).join(", ") : "none"}`);
      return { url: capture.url, type: streamTypeForUrl(capture.url), quality: capture.quality, audioTrack: capture.track ?? null, headers, segments: [] };
    }),
  );
}
