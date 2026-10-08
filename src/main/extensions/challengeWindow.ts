// The visible half of the Cloudflare flow (core/extensions/cloudflare.ts): the site in a window of
// its own, for as long as the person needs to pass its check. The window shares the default session
// with the hidden pages (browserFetchHost.ts), so a check passed here also lets those through; the
// clearance cookie and this browser's User-Agent go back to core for the app's own requests.
import { BrowserWindow } from "electron";
import { CHALLENGE_PROBE_SCRIPT, destroyAllPooledWindows } from "./browserFetchHost";
import type { ChallengeSession } from "./browserBridge";

const POLL_MS = 500;
// Once the check is gone the page is still redirecting and writing its cookie; a short calm spell
// before reading it, so the cookie read is the one the site set after the check.
const SETTLE_MS = 1_000;
// Cloudflare's own cookies - the clearance and its bot-management companions. Nothing else from the
// site is taken: the app's requests must not start carrying a stranger's session.
const isCloudflareCookie = (name: string) => name === "cf_clearance" || name.startsWith("__cf") || name.startsWith("cf_");

export function solveChallengeInWindow(url: string): Promise<ChallengeSession | null> {
  const host = new URL(url).hostname;
  return new Promise((resolve) => {
    const parent = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows().find((w) => !w.isDestroyed() && w.isVisible());
    const win = new BrowserWindow({
      width: 520,
      height: 700,
      title: `Cloudflare — ${host}`,
      autoHideMenuBar: true,
      backgroundColor: "#0b0b0e",
      parent: parent ?? undefined,
      webPreferences: { sandbox: true, contextIsolation: true },
    });
    // The page title would otherwise replace ours with "Just a moment...".
    win.on("page-title-updated", (event) => event.preventDefault());

    let settled = false;
    let passedAt = 0;
    const finish = (session: ChallengeSession | null) => {
      if (settled) return;
      settled = true;
      clearInterval(poller);
      if (session) {
        // Pooled hidden pages may still be holding the check page; they reload on next use.
        destroyAllPooledWindows();
        if (!win.isDestroyed()) win.close();
      }
      resolve(session);
    };

    const poll = async () => {
      if (settled || win.isDestroyed()) return;
      let state: { challenged: boolean; ready: boolean } | null = null;
      try {
        state = (await win.webContents.executeJavaScript(CHALLENGE_PROBE_SCRIPT)) as { challenged: boolean; ready: boolean };
      } catch {
        // Between the check and the page it leads to.
      }
      if (!state || state.challenged || !state.ready) {
        passedAt = 0;
        return;
      }
      if (!passedAt) passedAt = Date.now();
      if (Date.now() - passedAt < SETTLE_MS || win.isDestroyed()) return;
      const all = await win.webContents.session.cookies.get({ url });
      const wanted = all.filter((cookie) => isCloudflareCookie(cookie.name));
      finish({
        cookies: Object.fromEntries(wanted.map((cookie) => [cookie.name, cookie.value])),
        cookieHeader: wanted.map((cookie) => `${cookie.name}=${cookie.value}`).join("; "),
        userAgent: win.webContents.getUserAgent(),
      });
    };
    const poller = setInterval(() => void poll(), POLL_MS);

    win.on("closed", () => finish(null));
    win.loadURL(url).catch(() => {
      // A failed navigation leaves the window showing the error; closing it is the person's call.
    });
  });
}
