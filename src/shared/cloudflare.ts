// How a source error says "this stopped at Cloudflare's check" (core/extensions/cloudflare.ts) on its
// way to the screen. Errors cross IPC as plain text, so the page to open for the check rides in the
// message itself, behind a marker the renderer looks for to offer the check next to the error.

const MARKER = /\[cloudflare-check:([^\]\s]+)\]/;

export function cloudflareCheckError(sourceId: string, url: string): Error {
  const host = URL.canParse(url) ? new URL(url).hostname : url;
  return new Error(`Source "${sourceId}" stopped at a Cloudflare check on ${host} [cloudflare-check:${url}]`);
}

/** The page whose check stopped this error's source, or null for any other error. */
export function cloudflareCheckOf(error: unknown): { url: string; host: string } | null {
  const message = error instanceof Error ? error.message : typeof error === "string" ? error : "";
  const url = message.match(MARKER)?.[1];
  if (!url || !URL.canParse(url)) return null;
  return { url, host: new URL(url).hostname };
}
