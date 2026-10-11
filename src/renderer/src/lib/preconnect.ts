// Warm connections to where a source's playback went last time, while its title page is open.
//
// The first episode of a session paid for connecting to the CDN and the player's host: Kodik's
// manifest took 384 ms on the first episode against ~90 ms on the next ones (phone log), the rest
// being the DNS lookup and TCP/TLS handshake. Opening those connections when the title page opens
// moves that out of the wait; the network client keeps them pooled for a few minutes. Only the
// connection is made - no playlist, no segment - so it costs a few kilobytes at most.
import { hibiki } from "./hibiki";

const STORAGE_KEY = "hibiki-playback-origins";
const MAX_ORIGINS_PER_SOURCE = 4;
// A pooled connection outlives this; warming the same origin again sooner would only add requests.
const REWARM_AFTER_MS = 60_000;

const warmedAt = new Map<string, number>();

function originOf(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.origin : null;
  } catch {
    return null;
  }
}

function readAll(): Record<string, string[]> {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}") as unknown;
    return parsed && typeof parsed === "object" ? (parsed as Record<string, string[]>) : {};
  } catch {
    return {};
  }
}

/** Notes where a source's playback just went: the stream's host and the player page it came from. */
export function rememberPlaybackOrigins(sourceId: string, urls: Array<string | null | undefined>): void {
  const origins = urls.map(originOf).filter((origin): origin is string => origin !== null);
  if (origins.length === 0) return;
  try {
    const all = readAll();
    const merged = [...new Set([...origins, ...(all[sourceId] ?? [])])].slice(0, MAX_ORIGINS_PER_SOURCE);
    if (merged.join() === (all[sourceId] ?? []).join()) return;
    all[sourceId] = merged;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
  } catch {
    // Storage unavailable: nothing is warmed next time, which is how it always was.
  }
}

/** Opens connections to the hosts this source's playback used last time. */
export function warmPlaybackOrigins(sourceId: string): void {
  const now = Date.now();
  const origins = (readAll()[sourceId] ?? []).filter((origin) => now - (warmedAt.get(origin) ?? 0) > REWARM_AFTER_MS);
  if (origins.length === 0) return;
  for (const origin of origins) warmedAt.set(origin, now);
  hibiki.player.preconnect(origins);
}
