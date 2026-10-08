// Signs a user into a source's own site the same way a real browser would, for sources whose
// ACCOUNT setting declares `webLoginUrl` instead of (or in addition to) a plain login+password API
// - VK/Discord/Telegram/passkey buttons, 2FA, a CAPTCHA, whatever that page actually asks for. None
// of it is something the host could reimplement generically, so it doesn't try to: a visible window
// on the source's real login page, for as long as it takes, and the resulting cookies handed to the
// extension's own `loginWeb(cookiesJson)` once one it named shows up - what those cookies actually
// mean (a session id, a bearer token buried in one of them, ...) is entirely the source's business,
// same as the credentials `login()` already hands straight through unopened.
import { BrowserWindow, session } from "electron";
import { logger } from "../../core/logger";

export interface HarvestedCookie {
  name: string;
  value: string;
  domain?: string;
  path?: string;
  secure?: boolean;
  httpOnly?: boolean;
}

// Polled rather than driven off session.cookies' own "changed" event - that event fires once per
// cookie write, including ones set long before the sign-in this window is actually waiting on (an
// analytics id, a theme preference, ...), and filtering "was this the one that matters" back out
// is exactly what polling the end state already does for free.
const POLL_MS = 700;

/** One source's login window open at a time - a second attempt while one is already up focuses it
 * instead of opening a confusing second copy of the same sign-in flow. */
const openWindows = new Map<string, BrowserWindow>();

export function loginViaWebview(sourceId: string, url: string, successCookieName: string): Promise<HarvestedCookie[]> {
  const existing = openWindows.get(sourceId);
  if (existing && !existing.isDestroyed()) {
    existing.focus();
    return Promise.reject(new Error("A sign-in window for this source is already open"));
  }

  if (!URL.canParse(url)) {
    return Promise.reject(new Error(`Source "${sourceId}" declares an invalid web login URL`));
  }

  return new Promise((resolve, reject) => {
    // A partition of its own, not the main window's session - this is a third-party site's real
    // login page, and its cookies have no business ending up anywhere near the app's own requests
    // (or another source's). Named (not ephemeral) so a returning sign-in can skip straight past a
    // "remember me" the site already set, instead of asking every single time.
    const partition = `persist:source-login-${sourceId}`;
    const win = new BrowserWindow({
      width: 460,
      height: 700,
      title: sourceId,
      autoHideMenuBar: true,
      webPreferences: { partition, sandbox: true },
    });
    openWindows.set(sourceId, win);
    // A VK/Discord-style OAuth hop that opens its own popup should still happen inside this flow,
    // not vanish into a window the person never sees open.
    win.webContents.setWindowOpenHandler(() => ({ action: "allow" }));

    let settled = false;
    let poller: ReturnType<typeof setInterval> | null = null;

    const cleanup = () => {
      if (poller) clearInterval(poller);
      poller = null;
      openWindows.delete(sourceId);
    };

    const finish = async () => {
      if (settled) return;
      let cookies: Electron.Cookie[];
      try {
        // Not scoped to the login page's own host: a site can (and this partition has already
        // shown, in practice, one that does) land its real session cookie on a different
        // subdomain than the one the login form itself lives on. The partition is already scoped
        // to this one source, so grabbing everything in it is exactly as safe and a lot less
        // fragile than guessing which host the cookie actually landed on.
        cookies = await session.fromPartition(partition).cookies.get({});
      } catch (error) {
        logger.error("ext", `${sourceId} web login cookie read failed: ${error instanceof Error ? error.message : String(error)}`);
        return;
      }
      if (!cookies.some((cookie) => cookie.name === successCookieName)) return;
      settled = true;
      cleanup();
      win.removeAllListeners("closed");
      win.close();
      resolve(cookies.map((cookie) => ({ name: cookie.name, value: cookie.value, domain: cookie.domain, path: cookie.path, secure: cookie.secure, httpOnly: cookie.httpOnly })));
    };

    win.on("closed", () => {
      if (settled) return;
      settled = true;
      cleanup();
      reject(new Error("Sign-in window closed"));
    });

    win.loadURL(url).catch((error: unknown) => {
      logger.error("ext", `${sourceId} web login failed to load ${url}: ${error instanceof Error ? error.message : String(error)}`);
    });

    poller = setInterval(() => void finish(), POLL_MS);
  });
}

/** Closes any sign-in windows still open - same reasoning as the pooled fetch/resolver windows'
 * own before-quit teardown (main/index.ts): nothing should outlive the app that opened it. */
export function destroyAllLoginWindows(): void {
  for (const win of openWindows.values()) {
    if (!win.isDestroyed()) win.destroy();
  }
  openWindows.clear();
}
