/**
 * Makes a provider-supplied stream address explicit before it reaches the player.
 *
 * A leading `//` means "use the current page's protocol" on the web. The packaged
 * renderer is a `file:` page, however, so Chromium turns such an address into a
 * local `file://…` URL instead of a network request. Downloaded episodes use the
 * explicit `hibiki-download:` scheme and deliberately pass through unchanged.
 */
export function playbackUrl(url: string): string {
  return url.startsWith("//") ? `https:${url}` : url;
}
