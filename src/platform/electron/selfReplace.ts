// Replacing the running app with a downloaded build, for the desktop platforms that have no installer
// to run: a macOS .app bundle (from the release's zip) and a Linux AppImage. Everything here is
// planning - which file to replace, and the little shell script that does it after the app has
// quit - so it stays testable; the one thing that needs the real system (unzipping with ditto,
// starting the script, quitting) is in appInstaller.ts.
//
// The app cannot swap itself while it runs, and a new copy cannot start while the old one holds the
// single-instance lock, so the work is handed to a script that waits for this process to exit,
// swaps the files, and starts the new app. If the swap fails the old app is put back and started.
import fs from "node:fs/promises";
import path from "node:path";

export interface ReplaceEnv {
  platform: NodeJS.Platform;
  /** The running executable: .../hibiki.app/Contents/MacOS/hibiki, or inside the AppImage's mount. */
  exePath: string;
  /** Set by AppImage's runtime to the .AppImage file itself. */
  appImage: string | undefined;
  /** This process: the script waits until it is gone. */
  pid: number;
  /** An installed app, not `electron .` from a checkout. */
  packaged: boolean;
}

/** A string as one word of a POSIX shell: wrapped in single quotes, with any inside closed and escaped. */
export function shellQuote(text: string): string {
  return `'${text.replace(/'/g, `'\\''`)}'`;
}

/** The .app bundle an executable inside it belongs to (the outermost one), or null. */
export function appBundleOf(exePath: string): string | null {
  // The first .app on the way down is the outermost: a helper app inside it has a Contents of its own.
  return /^(.*?\.app)\/Contents\//.exec(exePath.replace(/\\/g, "/"))?.[1] ?? null;
}

/**
 * Where the new build would go, or null when it should not be replaced from here: not an installed
 * app, the folder is not ours to write to (an app in /Applications put there by an administrator),
 * or the app is running from a disk image or a quarantined download (App Translocation), where
 * replacing it would only replace a temporary copy.
 */
export function replaceTarget(env: ReplaceEnv, canWrite: (directory: string) => boolean): string | null {
  if (!env.packaged) return null;
  if (env.platform === "darwin") {
    const bundle = appBundleOf(env.exePath);
    if (!bundle || bundle.startsWith("/Volumes/") || bundle.includes("/AppTranslocation/")) return null;
    return canWrite(path.posix.dirname(bundle)) ? bundle : null;
  }
  if (env.platform === "linux") {
    if (!env.appImage) return null;
    return canWrite(path.posix.dirname(env.appImage)) ? env.appImage : null;
  }
  return null;
}

/** Waits (up to ~30 s) for the app to quit, so its single-instance lock and its files are free. */
const WAIT_FOR_EXIT = `i=0; while kill -0 "$PID" 2>/dev/null && [ "$i" -lt 150 ]; do sleep 0.2; i=$((i+1)); done`;

export function macScript(opts: { pid: number; target: string; replacement: string }): string {
  return [
    "#!/bin/sh",
    `PID=${opts.pid}`,
    `TARGET=${shellQuote(opts.target)}`,
    `NEW=${shellQuote(opts.replacement)}`,
    `OLD="$TARGET.hibiki-old"`,
    WAIT_FOR_EXIT,
    `rm -rf "$OLD"`,
    // The old app is kept until the new one is in place, and put back if it cannot be.
    `if mv "$TARGET" "$OLD"; then`,
    `  if mv "$NEW" "$TARGET"; then`,
    `    rm -rf "$OLD"`,
    `  else`,
    `    rm -rf "$TARGET"`,
    `    mv "$OLD" "$TARGET"`,
    `  fi`,
    `fi`,
    // Downloaded by the app, so never quarantined - cleared anyway, as a second Gatekeeper prompt is the one thing this must not cause.
    `xattr -cr "$TARGET" 2>/dev/null`,
    `open -n "$TARGET"`,
    "",
  ].join("\n");
}

export function appImageScript(opts: { pid: number; target: string; replacement: string }): string {
  return [
    "#!/bin/sh",
    `PID=${opts.pid}`,
    `TARGET=${shellQuote(opts.target)}`,
    `NEW=${shellQuote(opts.replacement)}`,
    WAIT_FOR_EXIT,
    // A rename within one folder: the old file is replaced in one step, or not at all.
    `mv -f "$NEW" "$TARGET" || rm -f "$NEW"`,
    `exec "$TARGET"`,
    "",
  ].join("\n");
}

/**
 * Gets the replacement ready and returns the script that swaps it in. macOS: the zip is unzipped
 * (`extractZip` - ditto, which keeps the bundle intact) next to where it was downloaded. Linux: the
 * AppImage is copied beside the one that is running, on the same disk, so the swap is a rename.
 */
export async function prepareReplacement(
  downloaded: string,
  env: ReplaceEnv,
  target: string,
  extractZip: (zip: string, directory: string) => Promise<void>,
): Promise<string> {
  if (env.platform === "darwin") {
    const directory = path.join(path.dirname(downloaded), "extracted");
    await fs.rm(directory, { recursive: true, force: true });
    await fs.mkdir(directory, { recursive: true });
    await extractZip(downloaded, directory);
    const app = (await fs.readdir(directory)).find((name) => name.endsWith(".app"));
    if (!app) throw new Error("The update archive does not contain an app");
    return macScript({ pid: env.pid, target, replacement: path.join(directory, app) });
  }
  if (env.platform === "linux") {
    const staged = `${target}.hibiki-new`;
    await fs.rm(staged, { force: true });
    await fs.copyFile(downloaded, staged);
    await fs.chmod(staged, 0o755);
    return appImageScript({ pid: env.pid, target, replacement: staged });
  }
  throw new Error(`Cannot replace the app on ${env.platform}`);
}
