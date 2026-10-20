import { app, shell } from "electron";
import { logger } from "../../core/logger";
import type { AppInstallerPort } from "../types";
import { electronFiles } from "./files";

/**
 * Applies an app update on desktop. Each kind it supports is one branch of `install`; a kind added
 * to the release format that isn't handled here falls through `supports` to a manual download.
 *
 * Nothing to verify about the file itself: its SHA-256 was checked against the release's manifest
 * when it was downloaded (core/updates.ts), and desktop builds carry no signature to compare.
 */
export const electronAppInstaller: AppInstallerPort = {
  supports: (kind) => kind === "installer",
  canInstall: async () => true,
  requestPermission: async () => true,
  verify: async (path) => ((await electronFiles.exists(path)) ? { ok: true } : { ok: false, reason: "unreadable" }),
  async install(path, kind) {
    if (kind !== "installer") throw new Error(`Cannot apply a "${kind}" update`);
    // openPath resolves with an empty string on success, or a message on failure - it does not
    // reject, so the result has to be inspected rather than awaited for its own sake.
    const failure = await shell.openPath(path);
    if (failure) throw new Error(`Could not start the installer: ${failure}`);
    logger.info("update", "installer launched, quitting to let it replace the app");
    // A beat for the shell to actually take hold of the file before this process disappears.
    setTimeout(() => app.quit(), 500);
  },
};
