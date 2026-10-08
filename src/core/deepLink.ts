import type { DeepLinkWatchTarget } from "@shared/types";

// The OS-level scheme this app registers itself as the handler for (app.setAsDefaultProtocolClient
// in index.ts) - what makes a "Watch" button on a Discord Rich Presence card able to launch this
// app at all, the same mechanism Spotify/Steam use for their own presence buttons.
export const DEEP_LINK_SCHEME = "hibiki";

/** The one deep link this app currently understands: reopen a specific episode/dub. */
export function buildWatchDeepLink(target: DeepLinkWatchTarget): string {
  const segment = (value: string) => encodeURIComponent(value);
  return `${DEEP_LINK_SCHEME}://watch/${segment(target.sourceId)}/${segment(target.animeId)}/${segment(target.groupId)}/${segment(target.episodeId)}`;
}

/** Null for anything not a "hibiki://watch/..." link this app itself produced - a stray argv entry
 * (a file path, an unrelated flag) is not a deep link, and a malformed one is not worth guessing at. */
export function parseWatchDeepLink(raw: string): DeepLinkWatchTarget | null {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }
  if (url.protocol !== `${DEEP_LINK_SCHEME}:` || url.hostname !== "watch") return null;
  const [sourceId, animeId, groupId, episodeId] = url.pathname.split("/").filter(Boolean).map(decodeURIComponent);
  if (!sourceId || !animeId || !groupId || !episodeId) return null;
  return { sourceId, animeId, groupId, episodeId };
}

/** The first argv entry (a launch/second-instance command line) that looks like one of this app's
 * own deep links - Windows/Linux hand a protocol-invoked launch to this app as a plain argv entry,
 * unlike macOS's dedicated `open-url` event. */
export function findDeepLinkInArgv(argv: string[]): string | null {
  return argv.find((arg) => arg.startsWith(`${DEEP_LINK_SCHEME}://`)) ?? null;
}
