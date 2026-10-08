// Cloudflare's "checking your browser" wall, as the app's own HTTP meets it.
//
// A source's plain fetch() is not a browser: when a site turns Cloudflare's challenge on, every
// request gets the interstitial page (403/503) instead of the data, and the source fails with
// whatever its parser makes of that HTML. Nothing in the app can pass the check by itself - a
// managed challenge or a Turnstile checkbox needs a person - so the check is recognised here, the
// person is offered a window with the site in it (BrowserPort.solveChallenge) next to the error that
// names the check (shared/cloudflare.ts), and the clearance
// that window earns (the cf_clearance cookie, bound to the browser's User-Agent) is then sent with
// every later request to that site.
import { logger } from "../logger";
import { getPlatform } from "../platform";
import type { ChallengeSession } from "../../platform/types";

function header(headers: Record<string, string>, name: string): string {
  for (const [key, value] of Object.entries(headers)) if (key.toLowerCase() === name) return value;
  return "";
}

// Markers of the interstitial itself. A normal page proxied by Cloudflare also loads scripts from
// /cdn-cgi/challenge-platform/, so that path alone means nothing; these appear only on the check.
const CHALLENGE_BODY = /_cf_chl_opt|cf-chl-|cf_chl_|challenge-error-text|id="challenge-form"|cf-turnstile|<title>\s*(just a moment|один момент)/i;
// A firewall rule that simply refuses the visitor ("Sorry, you have been blocked", error 1020) has no
// check to pass: offering a window for it would only show the same refusal.
const BLOCKED_BODY = /you have been blocked|error code:?\s*10(0\d|1\d|20)\b/i;

/** Whether this answer is Cloudflare's challenge page rather than the site's own. */
export function isCloudflareChallenge(status: number, headers: Record<string, string>, body: string): boolean {
  if (header(headers, "cf-mitigated").toLowerCase() === "challenge") return true;
  if (status !== 403 && status !== 503 && status !== 429) return false;
  if (!/cloudflare/i.test(header(headers, "server"))) return false;
  const head = body.slice(0, 64_000);
  return CHALLENGE_BODY.test(head) && !BLOCKED_BODY.test(head);
}

/** A source's own error text for a check it could not pass by itself (APK sources on Android). */
export function challengeUrlInError(message: string): string | null {
  return message.match(/Cloudflare challenge could not be solved for (\S+)/i)?.[1] ?? null;
}

// --- clearances ----------------------------------------------------------------------------------
// Kept in memory: a cf_clearance lives from minutes to a day depending on the site, and an expired one
// just brings the check back, which is answered the same way. The hidden browser pages keep their own
// copy in the browser's cookie store, so they need nothing from here.
interface Clearance {
  cookies: Record<string, string>;
  userAgent: string;
}

const clearances = new Map<string, Clearance>();

function hostOf(url: string): string | null {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return null;
  }
}

/** The clearance for `host`, or for a parent domain it was earned on (the cookie covers subdomains). */
function clearanceFor(host: string): { host: string; clearance: Clearance } | null {
  const labels = host.split(".");
  for (let i = 0; i < labels.length - 1; i++) {
    const candidate = labels.slice(i).join(".");
    const clearance = clearances.get(candidate);
    if (clearance) return { host: candidate, clearance };
  }
  return null;
}

export function rememberClearance(url: string, session: ChallengeSession): void {
  const host = hostOf(url);
  if (!host || Object.keys(session.cookies).length === 0) return;
  clearances.set(host, { cookies: session.cookies, userAgent: session.userAgent });
  logger.info("cloudflare", `clearance stored for ${host} (${Object.keys(session.cookies).join(", ")})`);
}

/** Called when a request carrying a clearance was challenged again: the clearance has expired. */
export function forgetClearance(url: string): void {
  const host = hostOf(url);
  const found = host ? clearanceFor(host) : null;
  if (!found) return;
  clearances.delete(found.host);
  logger.info("cloudflare", `clearance for ${found.host} no longer accepted`);
}

/**
 * The request's headers with the site's clearance added, or null when there is none. The cookie is
 * merged into any Cookie the source sends itself, and the User-Agent becomes the browser's: Cloudflare
 * accepts the cookie only from the agent that earned it.
 */
export function withClearance(url: string, headers: Record<string, string>): Record<string, string> | null {
  const host = hostOf(url);
  const found = host ? clearanceFor(host) : null;
  if (!found) return null;
  const { cookies, userAgent } = found.clearance;
  const result: Record<string, string> = {};
  let cookieHeader = "";
  for (const [key, value] of Object.entries(headers)) {
    const lower = key.toLowerCase();
    if (lower === "cookie") cookieHeader = value;
    else if (lower !== "user-agent") result[key] = value;
  }
  const pairs = cookieHeader.split(";").map((part) => part.trim()).filter((part) => part && !(part.split("=")[0] in cookies));
  for (const [name, value] of Object.entries(cookies)) pairs.push(`${name}=${value}`);
  result.Cookie = pairs.join("; ");
  result["User-Agent"] = userAgent;
  return result;
}

// --- the person passes it ------------------------------------------------------------------------

const solving = new Map<string, Promise<boolean>>();

/**
 * Opens the site in a visible window for the person to pass the check, and keeps the clearance it
 * earns. True once passed, false if the window was closed first. One window per site: a second
 * request for the same site joins the first.
 */
export function solveChallenge(url: string): Promise<boolean> {
  const host = hostOf(url);
  if (!host) return Promise.resolve(false);
  const running = solving.get(host);
  if (running) return running;
  const pending = (async () => {
    logger.info("cloudflare", `opening the check for ${host}`);
    const session = await getPlatform().browser.solveChallenge(url);
    if (!session) {
      logger.info("cloudflare", `check window for ${host} closed without passing`);
      return false;
    }
    rememberClearance(url, session);
    logger.info("cloudflare", `check passed for ${host}`);
    return true;
  })().finally(() => solving.delete(host));
  solving.set(host, pending);
  return pending;
}
