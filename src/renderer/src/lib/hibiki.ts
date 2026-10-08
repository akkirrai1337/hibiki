import type { AnimeTitle, SearchRequest } from "@shared/types";
import type { HibikiApi } from "@shared/hibikiApi";

export type { HibikiApi, LogEntry } from "@shared/hibikiApi";

declare global {
  interface Window {
    hibiki: HibikiApi;
  }
}

export const hibiki = window.hibiki;

/** Lets React Query's AbortSignal terminate the actual extension worker, not merely ignore its
 * eventual answer in the renderer. Used for live search where a newer query supersedes the old. */
export function searchSource(sourceId: string, request: SearchRequest, signal?: AbortSignal): Promise<AnimeTitle[]> {
  if (!signal) return hibiki.sources.search(sourceId, request);
  if (signal.aborted) return Promise.reject(new DOMException("Search cancelled", "AbortError"));

  const requestId = crypto.randomUUID();
  const pending = hibiki.sources.search(sourceId, request, requestId);
  return new Promise<AnimeTitle[]>((resolve, reject) => {
    let settled = false;
    const onAbort = () => {
      if (settled) return;
      settled = true;
      signal.removeEventListener("abort", onAbort);
      hibiki.sources.cancelSearch(requestId);
      reject(new DOMException("Search cancelled", "AbortError"));
    };
    signal.addEventListener("abort", onAbort, { once: true });
    void pending.then(
      (result) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener("abort", onAbort);
        resolve(result);
      },
      (error) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener("abort", onAbort);
        reject(error);
      },
    );
  });
}

// Builds a src the player can actually load for a downloaded file - see main/index.ts's
// `hibiki-download` protocol handler for why this can't just be a plain `file://` path (blocked
// as cross-origin from the renderer's own origin in dev, where it's served over http). The whole
// absolute path travels as one url-encoded opaque segment, not real path segments, so it
// round-trips exactly regardless of platform-specific separators/drive letters.
// Android has no such scheme: its WebView serves app files from its own local server instead.
export function downloadFileUrl(filePath: string): string {
  if (hibiki.downloads.fileUrl) return hibiki.downloads.fileUrl(filePath);
  return `hibiki-download://local/${encodeURIComponent(filePath)}`;
}

// Same idea for the profile banner (see main/index.ts's `hibiki-profile` handler), just a bare
// filename rather than a whole path - there is only ever the one file.
export function profileBannerUrl(filename: string): string {
  // Android serves it its own way; desktop through main's hibiki-profile:// protocol.
  if (hibiki.profile.bannerUrl) return hibiki.profile.bannerUrl(filename);
  return `hibiki-profile://local/${encodeURIComponent(filename)}`;
}
