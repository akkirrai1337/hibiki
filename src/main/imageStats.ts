// What the page's pictures (posters, icons) cost this session, for the log - the desktop side of
// android/.../ImageStats.kt. Written every few minutes when it changed, so an exported log always
// carries a recent count and says whether posters are what eats the traffic.
import { session } from "electron";
import { logger } from "../core/logger";
import { createHostTally } from "@shared/trafficByHost";

const INTERVAL_MS = 2 * 60 * 1000;

// onCompleted only: a session keeps one listener per webRequest event, and onErrorOccurred is
// playerHeaders.ts's - pictures that never got an answer are not counted.
let networkBytes = 0;
const imagesByHost = createHostTally("pictures", (line) => logger.info("traffic", line));

/** Bytes of pictures downloaded (not served from cache) since the app started. */
export function imageBytesThisSession(): number {
  return networkBytes;
}

export function installImageStats(): void {
  let network = 0;
  let cached = 0;
  let failed = 0;
  session.defaultSession.webRequest.onCompleted({ urls: ["http://*/*", "https://*/*"] }, (details) => {
    if (details.resourceType !== "image") return;
    if (details.statusCode >= 400) failed++;
    else if (details.fromCache) cached++;
    else {
      network++;
      const length = Object.entries(details.responseHeaders ?? {}).find(([name]) => name.toLowerCase() === "content-length")?.[1]?.[0];
      networkBytes += Number(length) || 0;
      imagesByHost.add(details.url, Number(length) || 0);
    }
  });
  let last = "";
  setInterval(() => {
    // Content-Length is absent on a chunked answer, so the megabytes can read a little low.
    const line = `${network} from network (${(networkBytes / 1024 / 1024).toFixed(1)} MB), ${cached} from cache, ${failed} failed`;
    if (line === last) return;
    last = line;
    logger.info("images", `this session: ${line}`);
  }, INTERVAL_MS).unref();
}
