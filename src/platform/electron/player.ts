import { registerPlayerHeaderOrigin, registerPlayerHeaders, unregisterPlayerHeaders } from "../../main/playerHeaders";
import { resolveFinalStreamUrl } from "../../main/playerStream";
import type { PlayerPort } from "../types";

/** Headers are injected session-wide by main/playerHeaders.ts, so URLs need no rewriting. */
export const electronPlayer: PlayerPort = {
  registerHeaders: async (url, headers) => registerPlayerHeaders(url, headers),
  registerHeaderOrigin: registerPlayerHeaderOrigin,
  unregisterHeaders: unregisterPlayerHeaders,
  playableUrl: (_sessionId, url) => url,
  resolveFinalUrl: resolveFinalStreamUrl,
};
