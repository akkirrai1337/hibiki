// Sources whose sites serve heavy posters (AnimeVost's PNGs run to 700KB, DonghuaStream's originals
// are 1739x2560) hand them out through images.weserv.nl, which resizes and re-encodes them to WebP
// on its CDN. That is one more service in the way, so the app knows how to get back to the
// original when it fails.
const RESIZE_PROXY_HOSTS = new Set(["images.weserv.nl", "wsrv.nl"]);

/** The picture an image-proxy URL resizes, or null for any other URL. */
export function originalOfResizedImage(url: string): string | null {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  if (!RESIZE_PROXY_HOSTS.has(parsed.hostname)) return null;
  const original = parsed.searchParams.get("url");
  if (!original) return null;
  // The proxy takes the address with or without its scheme.
  return /^https?:\/\//i.test(original) ? original : `https://${original.replace(/^\/\//, "")}`;
}
