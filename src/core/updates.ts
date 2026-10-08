// Updating the app itself, for the platforms that do it in-app (Android). The release choice,
// the download and the checks before installing live here once; the platform only answers "is this
// package really ours" and opens the system installer (Platform.appInstaller).
//
// Nothing here downloads on its own: when to fetch (mobile data, auto-update) is the UI's call.
// Windows still goes through main/appUpdates.ts, which behaves the same way for the same releases.
import { isNewerVersion } from "@shared/appVersion";
import { isTrustedDownloadUrl, RELEASES_URL, selectUpdate, type GitHubRelease, type UpdateTarget } from "@shared/appUpdateSelection";
import type { AppUpdate } from "@shared/types";
import { logger } from "./logger";
import { getPlatform } from "./platform";

const CHECK_TIMEOUT_MS = 10_000;
const UPDATES_DIR = "updates";

const UPDATE_ERROR_CODES = [
  "permission-needed",
  "download-failed",
  "signature-mismatch",
  "wrong-package",
  "wrong-version",
  "not-newer",
  "unreadable",
] as const;

/** Why an update did not go through. The UI tells the causes apart by `code`, never by the text. */
export type UpdateErrorCode = (typeof UPDATE_ERROR_CODES)[number];

export class UpdateError extends Error {
  readonly code: UpdateErrorCode;

  constructor(code: UpdateErrorCode, message?: string) {
    super(message ?? code);
    this.name = "UpdateError";
    this.code = code;
  }
}

function updatesDirectory(): string {
  const { files, paths } = getPlatform();
  return files.join(paths.userData, UPDATES_DIR);
}

/** The release this build should be offered, or null - also when GitHub could not be reached. */
export async function checkForUpdate(target: UpdateTarget): Promise<AppUpdate | null> {
  const platform = getPlatform();
  const current = platform.app.version();
  try {
    const response = await platform.http.request({
      url: RELEASES_URL,
      headers: { Accept: "application/vnd.github+json", "User-Agent": `hibiki/${current}` },
      timeoutMs: CHECK_TIMEOUT_MS,
    });
    if (response.status < 200 || response.status >= 300) {
      logger.warn("update", `GitHub returned HTTP ${response.status} listing releases`);
      return null;
    }
    const update = selectUpdate(JSON.parse(response.body) as GitHubRelease[], current, target);
    if (!update) {
      logger.info("update", `no update available (running ${current})`);
      return null;
    }
    logger.info("update", `update available: ${current} -> ${update.version} (${update.fileName}, ${update.sizeBytes} bytes)`);
    return update;
  } catch (error) {
    logger.warn("update", `could not check for updates: ${error instanceof Error ? error.message : String(error)}`);
    return null;
  }
}

/**
 * Downloads the update's package and resolves its path on disk.
 *
 * Written to a `.part` file and renamed only once its size matches the release, so an interrupted
 * download is never mistaken for a finished one and handed to the installer.
 */
export async function downloadUpdate(
  update: AppUpdate,
  onProgress: (receivedBytes: number, totalBytes: number) => void,
): Promise<string> {
  if (!isTrustedDownloadUrl(update.downloadUrl)) throw new UpdateError("download-failed", "Refusing to download an update from an untrusted host");
  // The name ends up in a path: nothing but a file's own name may be left of it.
  if (!/^[\w.-]+$/.test(update.fileName)) throw new UpdateError("download-failed", "Unexpected file name in the release");

  const { files, downloads, app } = getPlatform();
  const directory = updatesDirectory();
  const target = files.join(directory, update.fileName);
  // Already here in full - a retry after the install permission was granted: nothing to fetch again.
  if (update.sizeBytes > 0 && (await files.stat(target))?.size === update.sizeBytes) {
    onProgress(update.sizeBytes, update.sizeBytes);
    return target;
  }
  // Whatever an earlier run left - a finished package that was never installed, or a half-written
  // .part - is dead weight once a new download starts.
  await files.remove(directory);
  await files.mkdir(directory);

  const partial = `${target}.part`;

  try {
    await downloads.fetchToFile({
      url: update.downloadUrl,
      headers: { "User-Agent": `hibiki/${app.version()}` },
      filePath: partial,
      append: false,
      onProgress: (received, total) => onProgress(received, total ?? update.sizeBytes),
    });
  } catch (error) {
    await files.remove(partial);
    throw new UpdateError("download-failed", error instanceof Error ? error.message : String(error));
  }

  const written = (await files.stat(partial))?.size ?? 0;
  // A truncated download that still answered 200 would otherwise be offered to the installer.
  if (update.sizeBytes > 0 && written !== update.sizeBytes) {
    await files.remove(partial);
    throw new UpdateError("download-failed", `Downloaded ${written} bytes but the release lists ${update.sizeBytes}`);
  }

  await files.rename(partial, target);
  logger.info("update", `downloaded ${update.version} to ${target}`);
  return target;
}

/**
 * Checks the downloaded package against the installed app and hands it to the system installer.
 * Fails with a typed `UpdateError`; a package that does not check out is deleted.
 */
export async function installUpdate(path: string, version: string): Promise<void> {
  const { appInstaller, files } = getPlatform();
  if (!appInstaller) throw new UpdateError("unreadable", "This platform does not install updates itself");

  const verdict = await appInstaller.verify(path, version);
  if (!verdict.ok) {
    await files.remove(path);
    const code = UPDATE_ERROR_CODES.find((known) => known === verdict.reason) ?? "unreadable";
    throw new UpdateError(code, verdict.reason);
  }
  // The permission is asked for by the UI, which can explain it first: here it only blocks.
  if (!(await appInstaller.canInstall())) throw new UpdateError("permission-needed");

  await appInstaller.install(path);
  logger.info("update", `installer opened for ${version}`);
}

/**
 * Removes a package left over from an update that has since been installed (or never finished).
 * Run at start: unless a file there is newer than the running app, it is spent.
 */
export async function cleanUpdateLeftovers(): Promise<void> {
  const { files, app } = getPlatform();
  const directory = updatesDirectory();
  const names = await files.list(directory);
  if (names.length === 0) return;
  const current = app.version();
  // A newer package may still be waiting for the person to press "Install"; keep that one.
  const stillWanted = names.some((name) => {
    const match = /^hibiki-v(.+)\.apk$/i.exec(name);
    return match !== null && isNewerVersion(match[1], current);
  });
  if (!stillWanted) await files.remove(directory);
}
