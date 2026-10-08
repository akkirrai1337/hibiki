/**
 * Posters and source icons are fetched by the renderer, so Chromium attaches this app's own page
 * as their Referer. Image CDNs read that header, and they do not agree on what they want from it:
 * AniTube's answers 403 to a foreign Referer, while AnimeLib's cover host answers 403 to a request
 * that carries none. Both were verified directly against the live hosts.
 *
 * What satisfies both is what a page on the image's own site would send: the image's own origin.
 * That is the one Referer no image host can object to, since it is the one its own pages produce.
 *
 * | host                | app origin | no Referer | own origin |
 * |---------------------|------------|------------|------------|
 * | anitube.in.ua       | 403        | 200        | 200        |
 * | cover.cdnlibs.org   | 200        | 403        | 200        |
 *
 * Origin is dropped rather than rewritten. A plain <img> does not send one at all, so anything
 * present here is this app's, and it means nothing to the host being asked.
 *
 * Playback traffic never reaches this. A URL with registered playback headers is answered before
 * this is consulted, and those carry the Referer the stream actually requires.
 */
export function headersForImageRequest(
  resourceType: string,
  url: string,
  requestHeaders: Record<string, string>,
): Record<string, string> {
  if (resourceType !== "image") return requestHeaders;
  const referer = imageOriginReferer(url);
  if (referer === null) return requestHeaders;
  const headers = Object.fromEntries(
    Object.entries(requestHeaders).filter(([name]) => {
      const lower = name.toLowerCase();
      return lower !== "referer" && lower !== "origin";
    }),
  );
  headers.Referer = referer;
  return headers;
}

/** Null for anything not fetched over HTTP - a local or bundled image has nobody to convince. */
function imageOriginReferer(url: string): string | null {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return `${parsed.origin}/`;
  } catch {
    return null;
  }
}
