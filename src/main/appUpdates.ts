// Checks GitHub Releases for a newer build, downloads the installer, and hands it to the OS.
// Mirrors the Android app's AppUpdateRepository (core/update/AppUpdateRepository.kt) - same
// endpoint shape, same "highest non-prerelease newer than the running version" rule - with the
// asset picked by filename rather than content type, since GitHub reports .exe uploads
// inconsistently as application/x-msdownload or application/octet-stream depending on the client
// that uploaded them.
import fs from "node:fs";
import fsp from "node:fs/promises";
import path from "node:path";
import { app, shell } from "electron";
import type { AppUpdate } from "@shared/types";
import { isTrustedDownloadUrl, RELEASES_URL, selectUpdate, type GitHubRelease } from "@shared/appUpdateSelection";
import { logger } from "../core/logger";

const CHECK_TIMEOUT_MS = 10_000;

/** Where a downloaded installer is kept until it's launched. Its own directory under the app's
 * temp path, so the cleanup below can empty it without guessing which files are ours. */
function downloadDir(): string {
  return path.join(app.getPath("temp"), "hibiki-updates");
}

export async function checkForUpdate(): Promise<AppUpdate | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
  try {
    const response = await fetch(RELEASES_URL, {
      headers: { Accept: "application/vnd.github+json", "User-Agent": `hibiki-desktop/${app.getVersion()}` },
      signal: controller.signal,
    });
    if (!response.ok) {
      logger.warn("update", `GitHub returned HTTP ${response.status} listing releases`);
      return null;
    }
    const current = app.getVersion();
    const update = selectUpdate((await response.json()) as GitHubRelease[], current);
    if (!update) {
      logger.info("update", `no update available (running ${current})`);
      return null;
    }
    logger.info("update", `update available: ${current} -> ${update.version} (${update.fileName}, ${update.sizeBytes} bytes)`);
    return update;
  } catch (error) {
    logger.warn("update", `could not check for updates: ${error instanceof Error ? error.message : String(error)}`);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Downloads the installer, reporting progress, and resolves its path on disk.
 *
 * Written to a `.part` file and renamed only once complete, so an interrupted download can never
 * be mistaken for a finished one and handed to the OS to execute.
 */
export async function downloadUpdate(
  update: AppUpdate,
  onProgress: (receivedBytes: number, totalBytes: number) => void,
): Promise<string> {
  if (!isTrustedDownloadUrl(update.downloadUrl)) throw new Error("Refusing to download an update from an untrusted host");

  const directory = downloadDir();
  await fsp.mkdir(directory, { recursive: true });
  // Anything left from a previous run - a completed installer that was never launched, or a
  // half-finished .part - is dead weight the moment a new download starts.
  for (const entry of await fsp.readdir(directory).catch(() => [] as string[])) {
    await fsp.rm(path.join(directory, entry), { force: true }).catch(() => {});
  }

  const target = path.join(directory, update.fileName);
  const partial = `${target}.part`;

  const response = await fetch(update.downloadUrl, { headers: { "User-Agent": `hibiki-desktop/${app.getVersion()}` } });
  if (!response.ok || !response.body) throw new Error(`Download failed with HTTP ${response.status}`);

  const total = Number(response.headers.get("content-length")) || update.sizeBytes;
  let received = 0;
  const handle = await fsp.open(partial, "w");
  try {
    // Streamed rather than buffered: the installer is ~120MB, and holding that in memory while
    // also reporting progress is pointless when it has to land on disk regardless.
    for await (const chunk of response.body as unknown as AsyncIterable<Uint8Array>) {
      await handle.write(chunk);
      received += chunk.byteLength;
      onProgress(received, total);
    }
  } finally {
    await handle.close();
  }

  const written = (await fsp.stat(partial)).size;
  // A truncated download that still produced a 200 would otherwise be launched as an installer.
  if (update.sizeBytes > 0 && written !== update.sizeBytes) {
    await fsp.rm(partial, { force: true }).catch(() => {});
    throw new Error(`Downloaded ${written} bytes but the release lists ${update.sizeBytes}`);
  }

  await fsp.rm(target, { force: true }).catch(() => {});
  await fsp.rename(partial, target);
  logger.info("update", `downloaded ${update.version} to ${target}`);
  return target;
}

/**
 * Launches the downloaded installer and quits, so it can replace files this process is holding
 * open. Resolves only if the launch *failed* - on success the app is on its way out.
 */
export async function installUpdate(installerPath: string): Promise<void> {
  if (!fs.existsSync(installerPath)) throw new Error("The downloaded installer is no longer on disk");
  // openPath resolves with an empty string on success, or a message on failure - it does not
  // reject, so the result has to be inspected rather than awaited for its own sake.
  const failure = await shell.openPath(installerPath);
  if (failure) throw new Error(`Could not start the installer: ${failure}`);
  logger.info("update", `installer launched, quitting to let it replace the app`);
  // A beat for the shell to actually take hold of the file before this process disappears.
  setTimeout(() => app.quit(), 500);
}
