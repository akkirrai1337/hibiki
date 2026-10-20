// Subtitle tracks a BROWSER-runtime resolver reports through HibikiResolver.subtitle(url, label,
// language) - MegaPlay (Anichi) puts its English tracks on the page as <track> elements. Both hosts
// (main/extensions/browserResolveHost.ts, platform/android/browserResolve.ts) collect them from the
// page the same way; this is the part they share: cleaning what the page reported, and the headers
// the player fetches the files with.
import type { SubtitleTrack } from "./types";

/** As the bridge stores it in the page: whatever the page's script handed over. */
export interface ReportedSubtitle {
  url: unknown;
  label?: unknown;
  language?: unknown;
}

const text = (value: unknown): string | null => (typeof value === "string" && value.trim() ? value.trim() : null);

/**
 * The tracks worth offering, once each: only http(s) files (a blob: or data: URL lives and dies
 * with the hidden page), and labelled so the picker never shows an empty row. Files on the subtitle
 * CDN are fetched as the embed page fetched them - from its origin, with it as the referrer.
 *
 * "As the page fetched them" is what a browser sends: the page's whole address to its own host, but
 * only its origin ("https://megaplay.buzz/") to another one. A subtitle CDN that is another host
 * and checks the Referer takes the second - MegaPlay's answers 403 to the embed page's full address.
 */
export function subtitleTracksFrom(reported: ReportedSubtitle[] | null | undefined, embedUrl: string, userAgent?: string): SubtitleTrack[] {
  let embedOrigin: string | null = null;
  try {
    embedOrigin = new URL(embedUrl).origin;
  } catch {
    // A malformed embed URL still resolved; the Referer alone is what most CDNs check.
  }
  const headersFor = (trackUrl: string): Record<string, string> => {
    let sameOrigin = true;
    try {
      sameOrigin = embedOrigin === null || new URL(trackUrl).origin === embedOrigin;
    } catch {
      // Not an address after all: kept with the full referrer, as before.
    }
    const headers: Record<string, string> = { Referer: sameOrigin || embedOrigin === null ? embedUrl : `${embedOrigin}/` };
    if (embedOrigin) headers.Origin = embedOrigin;
    if (userAgent) headers["User-Agent"] = userAgent;
    return headers;
  };

  const seen = new Set<string>();
  const tracks: SubtitleTrack[] = [];
  for (const entry of reported ?? []) {
    const url = text(entry?.url);
    if (!url || !/^https?:\/\//i.test(url) || seen.has(url)) continue;
    seen.add(url);
    const language = text(entry.language);
    // A "thumbnails" track is the seek-bar preview sprite sheet, not something to read.
    const label = text(entry.label);
    if (label && /thumbnail/i.test(label)) continue;
    if (/thumbnails?\.vtt(?:[?#]|$)/i.test(url)) continue;
    tracks.push({ url, label: label ?? language ?? `Track ${tracks.length + 1}`, language, headers: headersFor(url) });
  }
  return tracks;
}
