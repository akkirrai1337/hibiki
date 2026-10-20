import { execFile, spawn } from "node:child_process";
import fs from "node:fs";
import { promisify } from "node:util";
import { app, shell } from "electron";
import { logger } from "../../core/logger";
import type { AppInstallerPort } from "../types";
import { electronFiles } from "./files";
import { prepareReplacement, replaceTarget, type ReplaceEnv } from "./selfReplace";

const run = promisify(execFile);

function replaceEnv(): ReplaceEnv {
  return { platform: process.platform, exePath: app.getPath("exe"), appImage: process.env.APPIMAGE, pid: process.pid, packaged: app.isPackaged };
}

function canWrite(directory: string): boolean {
  try {
    fs.accessSync(directory, fs.constants.W_OK);
    return true;
  } catch {
    return false;
  }
}

/**
 * Applies an app update on desktop. Each kind it supports is one branch of `install`; a kind added
 * to the release format that isn't handled here falls through `supports` to a manual download.
 *
 * Nothing to verify about the file itself: its SHA-256 was checked against the release's manifest
 * when it was downloaded (core/updates.ts), and desktop builds carry no signature to compare.
 */
export const electronAppInstaller: AppInstallerPort = {
  // "replace" only where the running copy can really be swapped (see replaceTarget): an app that is
  // not in a folder this user can write to is offered as a manual download instead of failing half way.
  supports: (kind) => (kind === "installer" ? process.platform === "win32" : kind === "replace" && replaceTarget(replaceEnv(), canWrite) !== null),
  canInstall: async () => true,
  requestPermission: async () => true,
  verify: async (path) => ((await electronFiles.exists(path)) ? { ok: true } : { ok: false, reason: "unreadable" }),
  async install(path, kind) {
    if (kind === "replace") {
      const env = replaceEnv();
      const target = replaceTarget(env, canWrite);
      if (!target) throw new Error("This copy of the app cannot be replaced in place");
      // `ditto` is macOS's own unzip, the one that keeps a bundle's links and permissions intact.
      const script = await prepareReplacement(path, env, target, async (zip, directory) => void (await run("/usr/bin/ditto", ["-xk", zip, directory])));
      spawn("/bin/sh", ["-c", script], { detached: true, stdio: "ignore" }).unref();
      logger.info("update", `replacing ${target} once this app has quit`);
      setTimeout(() => app.quit(), 300);
      return;
    }
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
