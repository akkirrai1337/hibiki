// Some sources require request headers (Referer, User-Agent, ...) that the Fetch/XHR spec
// forbids scripts from setting themselves (Referer especially — see VideoPlayer.tsx), and a plain
// `<video src>` request offers no header hook at all. The only place these can actually be
// injected is Electron's session-level webRequest API in the main process, keyed by origin (an
// HLS playlist's segment requests share the manifest's origin but not its exact URL, so exact-URL
// keying would miss them).
import { session } from "electron";
import { randomUUID } from "node:crypto";
import { PlayerHeaderRegistry } from "@shared/playerHeaderRegistry";
import { headersForImageRequest } from "@shared/imageRequestHeaders";
import { logger } from "../core/logger";

const headerRegistry = new PlayerHeaderRegistry();

export function registerPlayerHeaders(url: string, headers: Record<string, string> | null | undefined): string {
  const sessionId = randomUUID();
  headerRegistry.register(sessionId, url, headers ?? {});
  return sessionId;
}

export function registerPlayerHeaderOrigin(sessionId: string, url: string): boolean {
  return headerRegistry.registerOrigin(sessionId, url);
}

// Only the app's own renderer (hls.js, the <video>/<track> elements) - the hidden resolver and
// challenge windows share this session too, and their pages must never inherit a playback's headers.
function isPlayerRequest(details: { resourceType: string; initiator?: string }): boolean {
  if (details.resourceType !== "xhr" && details.resourceType !== "media") return false;
  const initiator = details.initiator;
  return !initiator || initiator === "null" || initiator.startsWith("file:") || /^https?:\/\/localhost(:\d+)?$/.test(initiator);
}

export function unregisterPlayerHeaders(sessionId: string): void {
  headerRegistry.unregister(sessionId);
}

// Response-side counterpart of the request-header injection above: hls.js fetches the manifest
// and every segment via XHR/fetch, which the browser subjects to CORS - and most stream CDNs
// don't bother sending Access-Control-Allow-Origin at all, since they only ever expected to be
// loaded from a matching <video>/native player, not cross-origin JS. The Referer/User-Agent
// injection above can't fix that (CORS is decided by the *response* headers, not the request).
//
// This was originally scoped to just the origin of the master playlist URL we registered headers
// for, on the assumption that's the only origin involved - wrong in practice: a multi-bitrate HLS
// stream's master playlist commonly points sub-playlists/segments at a *different* CDN host (seen
// live: Miruro's master playlist resolves through repackager.wixmp.com but the actual segments
// come from video.wixstatic.com), so scoping by "the one URL we know about" left every other host
// in the same stream still blocked, and the player just spun on "buffering" forever with no error
// surfaced anywhere. This app's renderer never browses arbitrary untrusted pages (only its own
// UI and whatever a source extension resolves for playback), so relaxing CORS session-wide is a
// safe trade here.
const CORS_RESPONSE_HEADER_NAMES = [
  "access-control-allow-origin",
  "access-control-allow-methods",
  "access-control-allow-headers",
  "access-control-allow-credentials",
];

export function installPlayerHeaderInjector(): void {
  session.defaultSession.webRequest.onBeforeSendHeaders((details, callback) => {
    const extra = headerRegistry.headersFor(details.url);
    if (extra) {
      callback({ requestHeaders: { ...details.requestHeaders, ...extra } });
      return;
    }
    const playback = isPlayerRequest(details) ? headerRegistry.playbackHeaders() : undefined;
    if (playback) {
      callback({ requestHeaders: { ...details.requestHeaders, ...playback } });
      return;
    }
    callback({ requestHeaders: headersForImageRequest(details.resourceType, details.url, details.requestHeaders) });
  });

  // Playback CDNs commonly redirect between numbered edge hosts (Kodik currently does
  // p12.solodcdn.com -> p13.solodcdn.com). Chromium does not carry a Referer injected by
  // onBeforeSendHeaders across that cross-origin redirect, and the origin-only registry above
  // therefore used to miss both the redirected manifest request and every relative segment that
  // followed it. The same stream works in the provider's website because those requests retain
  // the embed page's browser context. Teach the destination origin about the exact same playback
  // headers before Chromium follows the redirect.
  session.defaultSession.webRequest.onBeforeRedirect((details) => {
    try {
      const sourceOrigin = new URL(details.url).origin;
      const destinationOrigin = new URL(details.redirectURL).origin;
      if (headerRegistry.followRedirect(details.url, details.redirectURL)) {
        logger.debug("player", `carried playback headers across redirect ${sourceOrigin} -> ${destinationOrigin}`);
      }
    } catch {
      // Ignore malformed/non-network redirect URLs; no playback header can be associated safely.
    }
  });

  // hls.js can only report status 0 for failures below HTTP (CORS, DNS, TLS, a reset connection).
  // Electron has the useful net error and final URL here, so retain them in the same exported log
  // users already attach for playback failures. Limit this to media/XHR requests to avoid noise
  // from unrelated images or API calls.
  session.defaultSession.webRequest.onErrorOccurred((details) => {
    if (details.resourceType !== "xhr" && details.resourceType !== "media") return;
    // An aborted request is this app cancelling it - switching dub or quality, seeking, tearing a
    // player down - not a failure. hls.js has a dozen segments in flight at any moment, so one
    // switch used to write twenty ERROR lines about requests that were meant to stop.
    const level = details.error === "net::ERR_ABORTED" ? "debug" : "error";
    logger[level]("player", `${details.resourceType} request failed: ${details.error} ${details.url}`);
  });

  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    const responseHeaders: Record<string, string[]> = {};
    for (const [key, value] of Object.entries(details.responseHeaders ?? {})) {
      if (!CORS_RESPONSE_HEADER_NAMES.includes(key.toLowerCase())) responseHeaders[key] = value;
    }
    responseHeaders["Access-Control-Allow-Origin"] = ["*"];
    responseHeaders["Access-Control-Allow-Methods"] = ["GET, HEAD, OPTIONS"];
    responseHeaders["Access-Control-Allow-Headers"] = ["*"];
    callback({ responseHeaders });
  });
}
