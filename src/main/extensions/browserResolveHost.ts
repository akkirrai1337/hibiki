// Host side of the "BROWSER" runtime player-resolver contract (see hibiki-sources/README.md's
// Player resolvers section) - the desktop equivalent of Android's BrowserPlayerWebViewExtractor.
// A resolver like extractors/alloha.js can't get a stream with plain HTTP (the page's own JS picks
// a quality/CDN URL client-side, sometimes only after being told to switch quality); instead it
// exposes Provider.browserScript(linkJson), a plain-JS payload meant to run *inside* the loaded
// embed page and report findings back through a `HibikiResolver.*` bridge - mirrors Android's
// WebView + @JavascriptInterface bridge one-to-one, just backed by a hidden BrowserWindow instead.
import { BrowserWindow } from "electron";
import type { PlayerLink, SubtitleTrack } from "@shared/types";
import { subtitleTracksFrom, type ReportedSubtitle } from "@shared/resolverSubtitles";
import { logger } from "../../core/logger";

// Anything that can run JS in a given browsing context and give back its completion value - a
// plain WebContents (no wrapper iframe was used) or an Electron WebFrameMain for a specific
// subframe (see performBrowserResolve's iframe path). Electron's WebFrameMain.executeJavaScript
// runs from the privileged browser process, so - unlike page-level JS - it isn't blocked by the
// same-origin policy that stops the *parent* page from reaching into a cross-origin iframe.
interface ScriptTarget {
  executeJavaScript(code: string): Promise<unknown>;
}

// Probing starts fast and backs off to PROBE_DELAY_MS. A resolver whose page already has its
// stream URL - the common case for a healthy embed - used to sit through a flat 500ms before
// anyone looked; the tail of the loop is unchanged, so a slow page costs exactly what it did.
const FIRST_PROBE_DELAY_MS = 100;
const PROBE_DELAY_MS = 500;
const MAX_PROBES = 30;
const TIMEOUT_MS = 25_000;
const NAVIGATION_TIMEOUT_MS = 10_000;
const VALIDATION_TIMEOUT_MS = 5_000;
const SETTLE_MS = 600;
const MAX_VALIDATION_CONCURRENCY = 3;

function resolverUrlLabel(raw: string): string {
  try {
    const url = new URL(raw);
    const tail = url.pathname.split("/").filter(Boolean).slice(-2).map((part) =>
      part.length > 24 ? `${part.slice(0, 8)}…` : part,
    ).join("/");
    return `${url.host}/${tail || "…"}${url.search ? "?…" : ""}`;
  } catch {
    return "<invalid-url>";
  }
}

// Same net a bare <video src> or hls.js request would resolve to - not sniffing content-type,
// because captures come from a network-request hook (see below) that only sees the URL, same as
// Android's shouldInterceptRequest-based capture.
const MEDIA_URL_PATTERN = /\.(m3u8|mpd|mp4)(\?|#|$)/i;
const PLAYLIST_URL_PATTERN = /\.m3u8(\?|#|$)/i;
const PLAYLIST_HEAD_BYTES = 2048;
// A request literally for ".../master.m3u8": the player already asked for the playlist that lists every
// rendition, so waiting for more requests to settle can only add renditions it already knows about.
// buildResult still validates it before anything is returned.
const MASTER_PLAYLIST_REQUEST_PATTERN = /\/master(?:[-_.][^/?#]*)?\.m3u8(?:[?#]|$)/i;
// An HLS master playlist lists every rendition, which is exactly what the resolver script spends
// its time collecting one quality at a time. Finding one means the collecting is already done.

// Transport framing, per-request scope, and conditional/caching headers: all of them describe the
// one request they were captured from, not the stream. Cookies are dropped here and re-added below
// from the media CDN's own jar rather than the embed page's.
const REPLAYABLE_HEADER_DENYLIST = new Set([
  "host",
  "connection",
  "content-length",
  "accept-encoding",
  "range",
  "if-range",
  "if-none-match",
  "if-modified-since",
  "if-match",
  "if-unmodified-since",
  "cookie",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "priority",
  "content-type",
]);

// Not every matching request is real content - a JS video-player library (Plyr, Video.js, ...)
// commonly has its <video> element point at a tiny placeholder/poster asset on its own CDN before
// the page ever loads whatever it's actually meant to play, and that placeholder request matches
// MEDIA_URL_PATTERN just as well as a real stream would (seen live: cdn.plyr.io/static/blank.mp4
// captured as a "candidate" for an Alloha resolve, ahead of the two real streams it also found).
const PLACEHOLDER_URL_PATTERN = /cdn\.plyr\.io\/static\/blank\.mp4/i;

// Electron permits one onBeforeRequest listener per session, and these hidden windows share the
// app's default session (same as browserFetchHost.ts's pooled windows) so a challenge solved there
// still applies here. One dispatcher is installed per session and captures are routed by
// webContents id - installing a listener per resolve let two simultaneous player/download resolves
// overwrite each other, and either one's cleanup then removed the other's listener as well.
//
// Installed once and left in place, never torn down between resolves. Swapping a webRequest
// listener in or out rebuilds the session's request proxy, and requests already in flight through
// it can die with net::ERR_FAILED - which on this session means the segments of whatever is
// playing, since a resolve for a newly picked dub overlaps the stream still running. Leaving it
// installed costs a map lookup per request, against a proxy playerHeaders.ts keeps on this session
// permanently anyway.
//
// The handler also decides whether a request is allowed to go out at all, which is what makes the
// referring-page load below cheap - see loadRefererDocument.
type RequestHandler = (details: Electron.OnBeforeRequestListenerDetails) => { cancel: boolean };
type SentHeadersHandler = (details: Electron.OnSendHeadersListenerDetails) => void;

const networkCaptureByWebContents = new Map<number, RequestHandler>();
const sentHeadersCaptureByWebContents = new Map<number, SentHeadersHandler>();
const hookedSessions = new WeakSet<Electron.Session>();

function addNetworkCapture(
  session: Electron.Session,
  webContentsId: number,
  handler: RequestHandler,
  sentHeadersHandler: SentHeadersHandler,
): void {
  networkCaptureByWebContents.set(webContentsId, handler);
  sentHeadersCaptureByWebContents.set(webContentsId, sentHeadersHandler);
  if (hookedSessions.has(session)) return;
  hookedSessions.add(session);
  session.webRequest.onBeforeRequest((details, callback) => {
    const handle = details.webContentsId !== undefined ? networkCaptureByWebContents.get(details.webContentsId) : undefined;
    callback(handle ? handle(details) : {});
  });
  // onBeforeRequest only tells us the URL. By onSendHeaders Chromium has added the Client Hints,
  // Fetch Metadata, cookies and referrer that made the provider page's own request acceptable to
  // its CDN. Preserve that exact successful request identity for the renderer instead of trying
  // to reconstruct an ever-changing browser fingerprint by hand.
  session.webRequest.onSendHeaders((details) => {
    const handle = details.webContentsId !== undefined
      ? sentHeadersCaptureByWebContents.get(details.webContentsId)
      : undefined;
    handle?.(details);
  });
}

function removeNetworkCapture(webContentsId: number): void {
  networkCaptureByWebContents.delete(webContentsId);
  sentHeadersCaptureByWebContents.delete(webContentsId);
}

type CaptureKind = "master" | "video" | "audio" | "stream" | "network";

interface Capture {
  url: string;
  kind: CaptureKind;
  quality: string | null;
  /** The audio track it carries, when the resolver reported several (HibikiResolver.track). */
  track?: string | null;
}

export interface ResolvedStream {
  url: string;
  type: string; // "HLS" | "MP4" | "DASH" - matches the Node-resolver contract (see execute.ts)
  quality: string | null;
  audioTrack?: string | null;
  headers: Record<string, string>;
  segments: [];
  subtitles?: SubtitleTrack[];
}

// Every resolve used to build and tear down its own hidden window, which means a full Chromium
// renderer process spawn per embed - paid again for each mirror the fallback chain tries. The
// windows are kept for a while and handed to the next resolve instead.
//
// A pooled window is remembered *together with the referring page it is currently showing*, so a
// second episode from the same source skips that page load entirely - the iframe below is simply
// re-injected into the document already there. The URL is tracked here rather than read back off
// the page on purpose: the decision "do I have a usable referer document" must never be answered
// by a window still showing some *other* embed's page, which is how an iframe ends up injected
// into a stranger's document.
const RESOLVER_WINDOW_IDLE_TTL_MS = 5 * 60_000;
const RESOLVER_WINDOW_RESET_TIMEOUT_MS = 2_000;
const MAX_IDLE_RESOLVER_WINDOWS = 2;

interface IdleResolverWindow {
  window: BrowserWindow;
  /** The referring page loaded in it, or null for a window holding nothing reusable. */
  refererUrl: string | null;
  timer: ReturnType<typeof setTimeout>;
}

const idleResolverWindows: IdleResolverWindow[] = [];

function takeIdleWindow(match: (entry: IdleResolverWindow) => boolean): IdleResolverWindow | null {
  for (let i = idleResolverWindows.length - 1; i >= 0; i--) {
    const entry = idleResolverWindows[i];
    if (!match(entry)) continue;
    idleResolverWindows.splice(i, 1);
    clearTimeout(entry.timer);
    if (entry.window.isDestroyed()) continue;
    return entry;
  }
  return null;
}

/** A pooled window plus whether it already holds `refererUrl` and needs no navigation. */
function acquireResolverWindow(refererUrl: string | null): { window: BrowserWindow; hasReferer: boolean } {
  if (refererUrl) {
    const matching = takeIdleWindow((entry) => entry.refererUrl === refererUrl);
    if (matching) return { window: matching.window, hasReferer: true };
  }
  const any = takeIdleWindow(() => true);
  if (any) return { window: any.window, hasReferer: false };
  const window = new BrowserWindow({ show: false, webPreferences: { sandbox: true, contextIsolation: true, images: false } });
  // The embed page's own player autoplays while the extractor waits for its stream requests, and a
  // hidden window still has a working audio output - the episode was audible for the seconds this
  // takes, before our own player had even started. Nothing here is ever meant to be heard.
  window.webContents.setAudioMuted(true);
  return { window, hasReferer: false };
}

async function releaseResolverWindow(win: BrowserWindow, refererUrl: string | null): Promise<void> {
  if (win.isDestroyed()) return;
  if (idleResolverWindows.length >= MAX_IDLE_RESOLVER_WINDOWS) {
    win.destroy();
    return;
  }
  // An embed that broke out of its frame has navigated the window somewhere else entirely; what is
  // on screen decides what this window may be pooled as, not what it was asked to load.
  const held = refererUrl && originOf(win.webContents.getURL()) === originOf(refererUrl) ? refererUrl : null;
  try {
    if (held) {
      // The embed is torn out now rather than at the start of the next resolve. Two reasons: an
      // idle pooled window must not sit there with somebody's player still loading and playing in
      // it, and the next resolve attaches its child-frame listener before injecting - a leftover
      // frame still navigating would be handed to it as though it were the new embed.
      await withTimeout(
        win.webContents.executeJavaScript(`(function () { if (document.body) document.body.innerHTML = ""; })();`),
        RESOLVER_WINDOW_RESET_TIMEOUT_MS,
        "reset timed out",
      );
    } else {
      // Nothing worth keeping, and the page may still be running scripts of its own: the document,
      // and every timer it started, goes away with the navigation.
      await withTimeout(win.loadURL("about:blank"), RESOLVER_WINDOW_RESET_TIMEOUT_MS, "reset timed out");
    }
  } catch {
    if (!win.isDestroyed()) win.destroy();
    return;
  }
  if (win.isDestroyed()) return;
  const timer = setTimeout(() => {
    const index = idleResolverWindows.findIndex((entry) => entry.window === win);
    if (index >= 0) idleResolverWindows.splice(index, 1);
    if (!win.isDestroyed()) win.destroy();
  }, RESOLVER_WINDOW_IDLE_TTL_MS);
  timer.unref?.();
  idleResolverWindows.push({ window: win, refererUrl: held, timer });
}

/** Releases every idle resolver window - called on app quit, alongside browserFetchHost's own. */
export function destroyIdleResolverWindows(): void {
  for (const entry of idleResolverWindows.splice(0)) {
    clearTimeout(entry.timer);
    if (!entry.window.isDestroyed()) entry.window.destroy();
  }
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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

async function loadURLBefore(win: BrowserWindow, url: string, deadline: number, options?: Electron.LoadURLOptions): Promise<void> {
  const timeoutMs = Math.min(NAVIGATION_TIMEOUT_MS, deadline - Date.now());
  try {
    await withTimeout(win.loadURL(url, options), timeoutMs, `Navigation timed out for ${url}`);
  } catch (error) {
    if (!win.isDestroyed()) win.webContents.stop();
    throw error;
  }
}

function buildLoadOptions(headers?: Record<string, string> | null): Electron.LoadURLOptions | undefined {
  if (!headers || Object.keys(headers).length === 0) return undefined;
  const options: Electron.LoadURLOptions = {};
  const extra: string[] = [];
  for (const [key, value] of Object.entries(headers)) {
    // Chromium computes Referer itself from its own referrer-policy engine for a top-level
    // navigation and does not honor one supplied through the generic extraHeaders string -
    // loadURL has a dedicated httpReferrer option for exactly this (seen live: a plain Node
    // fetch() with this same Referer got Alloha's real player page, but loadURL with it folded
    // into extraHeaders still got the "content not found" anti-hotlink response).
    if (key.toLowerCase() === "referer" || key.toLowerCase() === "referrer") options.httpReferrer = value;
    else extra.push(`${key}: ${value}`);
  }
  if (extra.length > 0) options.extraHeaders = extra.join("\n");
  return options;
}

// Defines window.HibikiResolver (the JS-side half of the bridge) plus a fallback video-element
// watcher for pages that never call the bridge themselves but do end up setting a real <video>
// src - mirrors Android's fixed VIDEO_ELEMENT_PROBE snippet appended to every probe.
const BRIDGE_SCRIPT = `
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
  if (!window.__hibikiVideoWatcherInstalled) {
    window.__hibikiVideoWatcherInstalled = true;
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
  }
`;

interface PageState {
  done: boolean;
  captures: Capture[];
  lastQuality: string | null;
  subtitles?: ReportedSubtitle[];
}

async function readPageState(target: ScriptTarget, deadline: number): Promise<PageState> {
  try {
    return (await withTimeout(
      target.executeJavaScript(
        `({ done: window.__hibikiDone === true, captures: window.__hibikiCaptures || [], lastQuality: window.__hibikiLastQuality || null, subtitles: window.__hibikiSubtitles || [] })`,
      ),
      deadline - Date.now(),
      "Browser resolver state probe timed out",
    )) as PageState;
  } catch {
    return { done: false, captures: [], lastQuality: null };
  }
}

/** Waits for the iframe injected by performBrowserResolve to finish its own navigation, then
 * returns the WebFrameMain for it - or null if none showed up in time, so the caller can fall
 * back to treating the top frame as the target instead of hanging forever. */
/**
 * The embed frame, as soon as it is usable - which is when its document has committed, not when
 * the last of its fonts and stylesheets has arrived.
 *
 * Waiting for did-frame-finish-load meant waiting out every trailing subresource of the embed page
 * before the resolver script was allowed to look at it, on the assumption that the player would
 * not exist before then. It usually exists far earlier, and when it does not the probe loop
 * already handles that case: the script answers "no-player" and is re-injected on the next probe,
 * a hundred milliseconds later. So the earlier of the two signals wins.
 */
function waitForChildFrame(win: BrowserWindow, timeoutMs: number): Promise<Electron.WebFrameMain | null> {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (frame: Electron.WebFrameMain | null) => {
      if (settled) return;
      settled = true;
      win.webContents.removeListener("did-frame-finish-load", onFrameLoad);
      win.webContents.removeListener("did-frame-navigate", onFrameNavigate);
      resolve(frame);
    };
    const findFrame = (frameProcessId: number, frameRoutingId: number): Electron.WebFrameMain | undefined =>
      win.webContents.mainFrame.framesInSubtree.find(
        (f) => f.processId === frameProcessId && f.routingId === frameRoutingId,
      );
    const onFrameLoad = (_event: unknown, isMainFrame: boolean, frameProcessId: number, frameRoutingId: number) => {
      if (isMainFrame) return;
      const frame = findFrame(frameProcessId, frameRoutingId);
      if (frame) finish(frame);
    };
    const onFrameNavigate = (
      _event: unknown,
      _url: string,
      httpResponseCode: number,
      _httpStatusText: string,
      isMainFrame: boolean,
      frameProcessId: number,
      frameRoutingId: number,
    ) => {
      // A frame that committed an error page has nothing to run a resolver script in; let the
      // finish-load path or the timeout answer for it instead.
      if (isMainFrame || httpResponseCode >= 400) return;
      const frame = findFrame(frameProcessId, frameRoutingId);
      if (frame) finish(frame);
    };
    win.webContents.on("did-frame-finish-load", onFrameLoad);
    win.webContents.on("did-frame-navigate", onFrameNavigate);
    setTimeout(() => finish(null), timeoutMs);
  });
}

function streamTypeForUrl(url: string): string {
  const clean = url.split("?")[0].split("#")[0].toLowerCase();
  if (clean.endsWith(".m3u8")) return "HLS";
  if (clean.endsWith(".mpd")) return "DASH";
  return "MP4";
}

/**
 * Runs a BROWSER-runtime resolver's `browserScript` payload against the given EMBED link's page
 * in a hidden BrowserWindow, capturing whatever stream URL(s) it (or a plain <video> element, or
 * the page's own network requests) surface, and returns them in the same shape a plain
 * Provider.resolve() call would - so callers (see runtime.ts) don't need to distinguish the two.
 */
export async function performBrowserResolve(
  link: PlayerLink,
  script: string,
  timeoutMs = TIMEOUT_MS,
  parentUrl?: string | null,
): Promise<ResolvedStream[]> {
  // Covers navigation, iframe setup, probing and validation. Previously the clock started only
  // after all navigation had completed, allowing a dead embed page to hang well beyond 25s.
  const deadline = Date.now() + Math.min(TIMEOUT_MS, timeoutMs);
  const resolveStartedAt = Date.now();
  const sourceRefererUrl = link.headers?.Referer ?? link.headers?.referer ?? null;
  let refererUrl = sourceRefererUrl;
  try {
    if (parentUrl) {
      const parsedParentUrl = new URL(parentUrl);
      if (parsedParentUrl.protocol === "http:" || parsedParentUrl.protocol === "https:") {
        refererUrl = parsedParentUrl.href;
      }
    }
  } catch {
    // Keep the source-provided referrer for malformed/non-HTTP resolver context URLs.
  }
  const { window: win, hasReferer } = acquireResolverWindow(refererUrl);
  logger.info("resolve", `browser pipeline started for ${link.playerName ?? "?"}: target=${resolverUrlLabel(link.url)}, window=${hasReferer ? "pooled with referer" : "navigation required"}`);
  const ses = win.webContents.session;
  const networkCaptures: Capture[] = [];
  const capturedRequestHeaders = new Map<string, Record<string, string>>();
  let currentQuality: string | null = null;
  // Only ever true while the referring page itself is loading - see loadRefererDocument.
  let documentOnly = false;
  // The referring page this window may be pooled under afterwards. Set only once the embed is
  // actually running as a child frame of that page - see the iframe branch below.
  let heldRefererUrl: string | null = null;

  // Read once, up front: `win.webContents` throws on a destroyed window, and the cleanup below
  // runs on exactly the paths where the window may already be gone.
  const webContentsId = win.webContents.id;
  addNetworkCapture(
    ses,
    webContentsId,
    (details) => {
      if (MEDIA_URL_PATTERN.test(details.url) && !PLACEHOLDER_URL_PATTERN.test(details.url)) {
        networkCaptures.push({ url: details.url, kind: "network", quality: currentQuality });
      }
      return { cancel: documentOnly && details.resourceType !== "mainFrame" };
    },
    (details) => {
      if (MEDIA_URL_PATTERN.test(details.url) && !PLACEHOLDER_URL_PATTERN.test(details.url)) {
        capturedRequestHeaders.set(details.url, { ...details.requestHeaders });
      }
    },
  );

  // The referring page is wanted for one thing only: a document at the right origin to host the
  // iframe from. Its own scripts, stylesheets, fonts and XHRs are dead weight here - the body is
  // wiped and replaced with the iframe immediately afterwards - but loadURL does not resolve until
  // they have all finished, which on a heavy source SPA is most of the time a resolve takes. So
  // everything below the top-level document is cancelled for the duration of that one navigation.
  const loadRefererDocument = async (url: string): Promise<boolean> => {
    const startedAt = Date.now();
    documentOnly = true;
    try {
      await loadURLBefore(win, url, deadline);
      logger.debug("resolve", `browser referer document ready in ${Date.now() - startedAt}ms: ${resolverUrlLabel(url)}`);
      return true;
    } catch (error) {
      // Some referring pages never fully settle even stripped down to their HTML. As long as the
      // window ended up at that origin, embedding the real link below still works.
      const usable = !win.isDestroyed() && originOf(win.webContents.getURL()) === originOf(url);
      logger.warn("resolve", `browser referer navigation failed in ${Date.now() - startedAt}ms; usable=${usable}: ${resolverUrlLabel(url)}; ${String(error)}`);
      return usable;
    } finally {
      documentOnly = false;
    }
  };

  try {
    // A real <iframe src> embed and a direct top-level loadURL() are NOT equivalent from the
    // target server's point of view, even with an identical Referer header: Chromium computes
    // the Sec-Fetch-Site/Sec-Fetch-Dest request headers from the actual navigation's initiator,
    // not from anything supplied via extraHeaders/httpReferrer - a direct loadURL() always
    // reports Sec-Fetch-Site: none, Sec-Fetch-Dest: document (indistinguishable from someone
    // typing the URL into an address bar), whereas a real embed reports cross-site/iframe. Seen
    // live: some resolvers' target pages (Alloha) actively reject the "none" case as suspicious.
    // So the link is embedded inside a real iframe on the resolver's declared referring page
    // instead of navigated to directly - which conveniently also matches what browserScript
    // payloads like alloha.js already expect (they look for `document.querySelector("iframe")`
    // themselves before falling back to searching the top window).
    let target: ScriptTarget = win.webContents;
    // A pooled window already showing this exact referring page needs no navigation at all - the
    // iframe injection below wipes the body first, so what is left of the previous resolve goes
    // with it.
    const refererStartedAt = Date.now();
    const refererReady = refererUrl ? hasReferer || (await loadRefererDocument(refererUrl)) : false;
    const refererMs = Date.now() - refererStartedAt;
    if (refererUrl && refererReady) {
      const frameLoaded = waitForChildFrame(win, Math.max(0, Math.min(8_000, deadline - Date.now())));
      await withTimeout(win.webContents.executeJavaScript(`
        (function () {
          var iframe = document.createElement("iframe");
          iframe.src = ${JSON.stringify(link.url)};
          iframe.referrerPolicy = "unsafe-url";
          iframe.style.cssText = "position:fixed;inset:0;width:100%;height:100%;border:0;";
          iframe.setAttribute("allow", "autoplay");
          if (!document.body) document.documentElement.appendChild(document.createElement("body"));
          document.body.innerHTML = "";
          document.body.appendChild(iframe);
        })();
      `), deadline - Date.now(), "Browser resolver iframe setup timed out");
      const embedStartedAt = Date.now();
      const childFrame = await frameLoaded;
      // The three phases that used to be one opaque wait, so a slow resolve can be attributed:
      // reaching the referring page, the embed frame committing, and the probing after it.
      logger.debug(
        "resolve",
        `embed ready: referer ${refererMs}ms${hasReferer ? " (pooled)" : ""}, frame ${Date.now() - embedStartedAt}ms; target=${resolverUrlLabel(link.url)}`,
      );
      // Falling back to the top frame (rather than throwing) matches the plain-loadURL behavior
      // this replaces when there's no usable Referer to embed against - some resolver still gets
      // a chance to work even without the real cross-site-embed context.
      if (childFrame) {
        target = childFrame;
        // Only now is the window genuinely holding this referring page, with the embed as a child
        // frame of it. Anything that navigated away below must not be pooled under that claim.
        heldRefererUrl = refererUrl;
      } else {
        await loadURLBefore(win, link.url, deadline, buildLoadOptions(link.headers));
      }
    } else {
      await loadURLBefore(win, link.url, deadline, buildLoadOptions(link.headers));
    }

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
    // One reachability check per URL for the whole resolve, shared between the master-playlist
    // check below and the final validation in buildResult.
    const probes = new Map<string, Promise<StreamProbe>>();
    // `master()` is an explicit part of the resolver contract. Re-fetching declared URLs merely
    // to rediscover #EXT-X-STREAM-INF adds a CDN round trip (or a CORS timeout) after the resolver
    // already did its provider-specific work. Network-only observations remain subject to
    // validation in buildResult(); only deliberate master reports are trusted.
    for (let probe = 0; probe < MAX_PROBES && !done && Date.now() < deadline; probe++) {
      if (!started) {
        // The script is expected to drive its own state machine forward (see alloha.js's
        // switchNext() timer chain) once it finds what it's looking for - re-injecting it after
        // that would just race a second concurrent run, so only retry while it's telling us it
        // isn't ready yet (alloha.js returns the literal string "no-player" for that case).
        // executeJavaScript's return value is the *completion value* of the executed code, same
        // as pasting it into a console - since the script is already its own invoked IIFE
        // ("(function(){...})();"), it must run as a bare expression statement here, not be
        // wrapped in another function, or its "no-player" return gets swallowed into that
        // wrapper's own (undefined) return instead of surfacing to us.
        let result: unknown;
        const scriptStartedAt = Date.now();
        try {
          result = await withTimeout(
            target.executeJavaScript(`${BRIDGE_SCRIPT}\n${script}`),
            deadline - Date.now(),
            "Browser resolver script timed out",
          );
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
      const state = await readPageState(target, deadline);
      currentQuality = state.lastQuality; // tags network captures made before the *next* tick
      reportedSubtitles.push(...(state.subtitles ?? []));
      done = state.done;
      if (probe < 8 || done || state.captures.length > 0 || networkCaptures.length > 0) {
        logger.debug("resolve", `browser probe ${probe + 1}: state read ${Date.now() - probeStartedAt}ms; done=${done}; captured=${state.captures.length + networkCaptures.length}; quality=${state.lastQuality ?? "?"}; elapsed=${Date.now() - resolveStartedAt}ms`);
      }

      const masters = state.captures.filter((capture) => capture.kind === "master" && !PLACEHOLDER_URL_PATTERN.test(capture.url));
      if (masters.length > 0) {
        logger.debug("resolve", `master playlist(s) captured on probe ${probe + 1}: ${masters.length}; stopping early`);
        return withSubtitles(await buildResult(masters, [], link, win, target, deadline, probes, capturedRequestHeaders));
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

      if (done) {
        return await withSubtitles(await buildResult(state.captures, networkCaptures, link, win, target, deadline, probes, capturedRequestHeaders));
      }
    }

    const finalState = await readPageState(target, deadline);
    logger.info("resolve", `browser probing finished after ${Date.now() - resolveStartedAt}ms: page=${finalState.captures.length}, network=${networkCaptures.length}; validating candidates`);
    reportedSubtitles.push(...(finalState.subtitles ?? []));
    return withSubtitles(await buildResult(finalState.captures, networkCaptures, link, win, target, deadline, probes, capturedRequestHeaders));
  } finally {
    removeNetworkCapture(webContentsId);
    void releaseResolverWindow(win, heldRefererUrl);
  }
}

const CAPTURE_KIND_RANK: Record<CaptureKind, number> = { master: 0, video: 1, audio: 2, stream: 3, network: 4 };

// Validates a captured URL from *inside* the same browsing context that captured it (the resolve
// window/frame itself), not via this process's own fetch(). Node's fetch (undici) and Chromium's
// network stack are genuinely different TLS/HTTP clients (different ClientHello, ALPN, connection
// reuse, ...) - a CDN that fingerprints the request (seen live: vkvideo.cloud, via Alloha) can
// happily serve the real player (which also runs inside Chromium, via hls.js/dash.js's own
// fetch/XHR) while rejecting a validation check made from Node, producing a false negative that
// throws away a URL that would actually have played fine. Running the check through the same
// engine the eventual player uses removes that mismatch.
interface StreamProbe {
  reachable: boolean;
  /** First bytes of the response, for playlists only - empty for everything else. */
  head: string;
}

/**
 * One reachability check per captured URL, cached for the whole resolve.
 *
 * For an HLS playlist the first couple of kilobytes come back with it, which is what tells a master
 * playlist (an index of every rendition) apart from a media playlist (one rendition's segments) -
 * see the probe loop, which stops the moment it has a master. Everything else asks for a single
 * byte, because a CDN that ignores Range would otherwise start sending a whole video file.
 */
async function probeStream(
  target: ScriptTarget,
  url: string,
  deadline: number,
  cache: Map<string, Promise<StreamProbe>>,
): Promise<StreamProbe> {
  const existing = cache.get(url);
  if (existing) return existing;

  const wantsHead = PLAYLIST_URL_PATTERN.test(url);
  const range = wantsHead ? `bytes=0-${PLAYLIST_HEAD_BYTES - 1}` : "bytes=0-0";
  const probe = (async (): Promise<StreamProbe> => {
    const startedAt = Date.now();
    try {
      const timeoutMs = Math.min(VALIDATION_TIMEOUT_MS, deadline - Date.now());
      const result = (await withTimeout(target.executeJavaScript(`
        (function () {
          var controller = new AbortController();
          var timer = setTimeout(function () { controller.abort(); }, ${VALIDATION_TIMEOUT_MS});
          return fetch(${JSON.stringify(url)}, { method: "GET", headers: { Range: ${JSON.stringify(range)} }, signal: controller.signal })
            .then(function (r) {
              if (!${JSON.stringify(wantsHead)}) return { ok: r.ok, status: r.status, head: "" };
              return r.text().then(function (text) {
                return { ok: r.ok, status: r.status, head: String(text).slice(0, ${PLAYLIST_HEAD_BYTES}) };
              });
            })
            .catch(function () { return { ok: false, status: 0, head: "" }; })
            .finally(function () { clearTimeout(timer); });
        })();
      `), timeoutMs, `Stream validation timed out for ${url}`)) as { ok: boolean; status: number; head: string };
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
  win: BrowserWindow,
  target: ScriptTarget,
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

  // Refreshed after capture, not before - a Cloudflare-style clearance cookie set while the page
  // ran (exactly what challenge() exists for elsewhere in this app) needs to be in the header set
  // handed back for the *next* request (the actual stream fetch), not the embed page's own load.
  const baseHeaders: Record<string, string> = {
    ...(link.headers ?? {}),
    "User-Agent": win.webContents.getUserAgent(),
    Referer: link.url,
    Accept: "*/*",
    "Sec-Fetch-Dest": "video",
    "Sec-Fetch-Mode": "no-cors",
    "Sec-Fetch-Site": "cross-site",
  };
  // Browser-runtime streams were requested by the third-party embed page itself. Some CDNs
  // validate Origin as well as Referer (Alloha's vkvideo.cloud endpoint returns 403 to hls.js
  // without it even though the exact same signed URL works inside alloha.yani.tv), so preserve
  // that request identity when the stream moves into Hibiki's renderer.
  try {
    baseHeaders.Origin = new URL(link.url).origin;
  } catch {
    // The URL was already usable enough to load in the browser resolver; leave malformed edge
    // cases to the existing validation/fallback path rather than failing a successful capture.
  }
  // Prefer candidates that a same-page fetch can validate. That fetch is still subject to CORS,
  // though, while media elements and Hibiki's player are not (the latter has response headers
  // relaxed in playerHeaders.ts). MegaPlay is the important real-world case: its player visibly
  // consumes the captured HLS URL, but a script fetch from the same frame is rejected, which used
  // to misclassify every Anichi stream as dead and force the raw EMBED iframe. Android already
  // trusts these browser captures and lets the player perform the authoritative media request.
  const reachable = new Array<boolean>(combined.length).fill(false);
  let nextValidation = 0;
  async function validateWorker(): Promise<void> {
    for (;;) {
      const index = nextValidation++;
      if (index >= combined.length) return;
      // A resolver explicitly calling master()/video()/stream() is authoritative provider logic,
      // equivalent to a NODE resolver returning the URL directly. Validating it again was both
      // redundant and expensive for cross-origin players such as MegaPlay. Requests merely seen
      // in the network log are less trustworthy and still take the existing reachability check.
      if (combined[index].kind !== "network") {
        reachable[index] = true;
        continue;
      }
      reachable[index] = (await probeStream(target, combined[index].url, deadline, probes)).reachable;
    }
  }
  // URLs the probe loop already checked answer from the cache, so this is usually far less work
  // than it looks - most often none at all.
  await Promise.all(Array.from({ length: Math.min(MAX_VALIDATION_CONCURRENCY, combined.length) }, validateWorker));
  const reachableCaptures = combined.filter((_capture, index) => reachable[index]);
  const selected = reachableCaptures.length > 0 ? reachableCaptures : combined;
  logger.info("resolve", `browser candidate checks finished in ${Date.now() - buildStartedAt}ms: reachable=${reachableCaptures.length}/${combined.length}; returning=${selected.length}`);
  if (reachableCaptures.length === 0) {
    logger.warn("resolve", `same-page validation was blocked for ${combined.length} captured stream(s); returning browser captures`);
  }
  return Promise.all(selected.map(async (capture): Promise<ResolvedStream> => {
    // Cookies belong to the media CDN, not to the embed page. The hidden browser and the renderer
    // share Electron's session, but hls.js uses a cross-origin XHR without credentials, so Chromium
    // does not attach those CDN cookies by itself. Android explicitly refreshes CookieManager for
    // every captured stream URL for the same reason. The old code instead copied MegaPlay's own
    // cookies onto cdn.imgnex.top, leaving out the clearance/token cookie that made the capture's
    // browser request work and producing the 403 seen as hls.js levelLoadError.
    const streamCookies = await win.webContents.session.cookies.get({ url: capture.url });
    const capturedHeaders = capturedRequestHeaders.get(capture.url);
    const headers = { ...baseHeaders, ...(capturedHeaders ?? {}) };
    // Chromium owns these headers, and a captured value is about the *hidden* request rather than
    // the one being made. That matters more than it looks: registered headers are spread over every
    // later request to the same origin (see playerHeaders.ts), so one captured `Range: bytes=0-`
    // from a progressive MP4 would pin every segment and every seek to the start of the file, and a
    // captured validator would answer 304 to a request that needs a body.
    for (const name of Object.keys(headers)) {
      if (REPLAYABLE_HEADER_DENYLIST.has(name.toLowerCase())) delete headers[name];
    }
    if (streamCookies.length > 0) headers.Cookie = streamCookies.map((cookie) => `${cookie.name}=${cookie.value}`).join("; ");
    logger.debug(
      "resolve",
      `captured browser headers for ${new URL(capture.url).hostname}: ${capturedHeaders ? Object.keys(capturedHeaders).join(", ") : "none"}`,
    );
    return {
      url: capture.url,
      type: streamTypeForUrl(capture.url),
      quality: capture.quality,
      audioTrack: capture.track ?? null,
      headers,
      segments: [],
    };
  }));
}
