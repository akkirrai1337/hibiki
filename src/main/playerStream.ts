// Follows a playback URL's redirects in the main process and reports where it actually lands.
//
// Why this has to happen outside the renderer: playback CDNs load-balance with a cross-origin
// redirect (Kodik hands out p14.solodcdn.com and immediately 302s to p13, verified live), and a
// redirect is the weakest point of a renderer-side stream fetch. hls.js loads the manifest by XHR,
// so the whole chain is subject to CORS - and these CDNs send no Access-Control-Allow-Origin at
// all. playerHeaders.ts papers over that by injecting one onto every response, but a *redirected*
// CORS request has to pass the check on the redirect hop too, and anything that goes wrong there
// surfaces to hls.js as status 0 with no diagnostic at all - which is exactly what an exported log
// showed for a Kodik stream that had resolved perfectly 29ms earlier.
//
// Here there is no CORS, no origin and no opinion about redirects, so the chain can simply be
// walked. Handing hls.js the final URL means its request never redirects in the first place, which
// removes the failure rather than retrying through it - and relative segment URLs resolve against
// that final URL, so they follow the stream to the same host instead of bouncing per segment.
import { logger } from "../core/logger";

const MAX_REDIRECT_HOPS = 5;
const RESOLVE_TIMEOUT_MS = 8000;
const RESOLVE_CACHE_TTL_MS = 30_000;

type CachedResolution = { url: string; expiresAt: number };
const resolvedUrls = new Map<string, CachedResolution>();
const resolvingUrls = new Map<string, Promise<string>>();

function resolutionKey(url: string, headers?: Record<string, string> | null): string {
  const normalized = Object.entries(headers ?? {})
    .map(([name, value]) => [name.toLowerCase(), value] as const)
    .sort(([a], [b]) => a.localeCompare(b));
  return `${url}\n${JSON.stringify(normalized)}`;
}

/**
 * The URL this one ends up at, or the input unchanged when it doesn't redirect or can't be
 * checked. Never throws: a stream that fails here should still be *tried* by the player, which
 * reports its own errors - this is an optimization of the request, not a gate on it.
 */
export async function resolveFinalStreamUrl(url: string, headers?: Record<string, string> | null): Promise<string> {
  const key = resolutionKey(url, headers);
  const cached = resolvedUrls.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.url;
  const existing = resolvingUrls.get(key);
  if (existing) return existing;

  let pending!: Promise<string>;
  pending = resolveFinalStreamUrlUncached(url, headers).then((resolved) => {
    resolvedUrls.set(key, { url: resolved, expiresAt: Date.now() + RESOLVE_CACHE_TTL_MS });
    return resolved;
  }).finally(() => {
    if (resolvingUrls.get(key) === pending) resolvingUrls.delete(key);
  });
  resolvingUrls.set(key, pending);
  return pending;
}

async function resolveFinalStreamUrlUncached(url: string, headers?: Record<string, string> | null): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), RESOLVE_TIMEOUT_MS);
  let current = url;
  try {
    for (let hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
      // GET rather than HEAD - a fair number of media CDNs answer HEAD with 405 or with a
      // different redirect than they'd give the real request. `redirect: "manual"` stops undici
      // from following it itself, so only the headers are needed and the body below is discarded
      // unread: nothing of the manifest (or, for a direct MP4, the file) is actually downloaded.
      const response = await fetch(current, {
        method: "GET",
        headers: headers ?? undefined,
        redirect: "manual",
        signal: controller.signal,
      });
      void response.body?.cancel();

      if (response.status < 300 || response.status >= 400) {
        if (current !== url) logger.info("player", `stream URL resolved through ${hop} redirect(s) to ${current}`);
        return current;
      }
      const location = response.headers.get("location");
      if (!location) return current;
      current = new URL(location, current).toString();
    }
    logger.warn("player", `stream URL still redirecting after ${MAX_REDIRECT_HOPS} hops: ${url}`);
    return current;
  } catch (error) {
    logger.warn("player", `could not pre-resolve stream URL ${url}: ${error instanceof Error ? error.message : String(error)}`);
    // The original URL, not the partially-walked one: a chain that failed midway is not evidence
    // that the hop before it is a better address than the one the resolver actually gave us.
    return url;
  } finally {
    clearTimeout(timer);
  }
}
