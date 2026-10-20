// Updating the app itself, on every platform. The release choice, the download and the checks
// before installing live here once; a platform only says which kinds of update it applies itself
// and how (Platform.appInstaller) - everything else is sent to the release page.
//
// Which file a platform takes comes from the release's own update manifest (see
// shared/appUpdateSelection.ts), keyed by Platform.app.updateKey(): supporting another platform is
// a line in CI's manifest script and an appInstaller here, never a change to the choosing below.
//
// Nothing here downloads on its own: when to fetch (mobile data, auto-update) is the UI's call.
import { isNewerVersion } from "@shared/appVersion";
import {
  buildUpdate,
  isReleasePageUrl,
  isTrustedDownloadUrl,
  manifestAssetOf,
  newerReleases,
  parseManifest,
  RELEASES_URL,
  type GitHubRelease,
} from "@shared/appUpdateSelection";
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

/** How many newer releases to look through for one that carries this platform's build. */
const MAX_CANDIDATES = 3;

/** The release this build should be offered, or null - also when GitHub could not be reached. */
export async function checkForUpdate(): Promise<AppUpdate | null> {
  const platform = getPlatform();
  const current = platform.app.version();
  const key = platform.app.updateKey();
  const headers = { Accept: "application/vnd.github+json", "User-Agent": `hibiki/${current}` };
  try {
    const response = await platform.http.request({ url: RELEASES_URL, headers, timeoutMs: CHECK_TIMEOUT_MS });
    if (response.status < 200 || response.status >= 300) {
      logger.warn("update", `GitHub returned HTTP ${response.status} listing releases`);
      return null;
    }
    const candidates = newerReleases(JSON.parse(response.body) as GitHubRelease[], current).slice(0, MAX_CANDIDATES);
    // Highest first, and the first one with a build for this platform wins: a newer release that
    // doesn't carry it yet (say, macOS finishing later than Windows) doesn't hide the older one that does.
    for (const candidate of candidates) {
      const asset = manifestAssetOf(candidate.release);
      if (!asset) {
        logger.info("update", `${candidate.version} has no update manifest`);
        continue;
      }
      // No GitHub-API Accept header here: this is a plain file download, not an API endpoint.
      const manifestResponse = await platform.http.request({
        url: asset.browser_download_url,
        headers: { "User-Agent": headers["User-Agent"] },
        timeoutMs: CHECK_TIMEOUT_MS,
      });
      if (manifestResponse.status < 200 || manifestResponse.status >= 300) {
        logger.warn("update", `manifest of ${candidate.version}: HTTP ${manifestResponse.status}`);
        continue;
      }
      const manifest = parseManifest(JSON.parse(manifestResponse.body));
      const built = manifest ? buildUpdate(candidate, manifest, key) : null;
      if (!built) {
        logger.info("update", `${candidate.version} has no build for ${key}`);
        continue;
      }
      // A kind this platform cannot apply is offered as what it will be: a trip to the release
      // page. The UI reads `kind` to tell the two apart before anything is downloaded or quit.
      const update: AppUpdate = platform.appInstaller?.supports(built.kind) ? built : { ...built, kind: "manual" };
      logger.info("update", `update available: ${current} -> ${update.version} (${update.fileName}, ${update.sizeBytes} bytes, ${update.kind})`);
      return update;
    }
    logger.info("update", `no update available (running ${current}, ${key})`);
    return null;
  } catch (error) {
    logger.warn("update", `could not check for updates: ${error instanceof Error ? error.message : String(error)}`);
    return null;
  }
}

/** True when the file is the one the release lists - or when this platform cannot hash a file, in
 * which case the package's own signature check (AppInstallerPort.verify) is what vouches for it. */
async function checksumMatches(path: string, update: AppUpdate): Promise<boolean> {
  const { files } = getPlatform();
  if (!files.sha256 || !update.sha256) return true;
  return (await files.sha256(path)) === update.sha256.toLowerCase();
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
  if (update.sizeBytes > 0 && (await files.stat(target))?.size === update.sizeBytes && (await checksumMatches(target, update))) {
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
  if (!(await checksumMatches(partial, update))) {
    await files.remove(partial);
    throw new UpdateError("download-failed", "The downloaded file does not match the checksum the release lists");
  }

  await files.rename(partial, target);
  logger.info("update", `downloaded ${update.version} to ${target}`);
  return target;
}

/**
 * Checks the downloaded package against the installed app and hands it to the system installer.
 * Fails with a typed `UpdateError`; a package that does not check out is deleted.
 */
export async function installUpdate(path: string, version: string, kind: AppUpdate["kind"]): Promise<void> {
  const { appInstaller, files } = getPlatform();
  if (!appInstaller?.supports(kind)) throw new UpdateError("unreadable", `This platform does not apply "${kind}" updates itself`);

  const verdict = await appInstaller.verify(path, version);
  if (!verdict.ok) {
    await files.remove(path);
    const code = UPDATE_ERROR_CODES.find((known) => known === verdict.reason) ?? "unreadable";
    throw new UpdateError(code, verdict.reason);
  }
  // The permission is asked for by the UI, which can explain it first: here it only blocks.
  if (!(await appInstaller.canInstall())) throw new UpdateError("permission-needed");

  await appInstaller.install(path, kind);
  logger.info("update", `${kind} update to ${version} started`);
}

/**
 * The whole update in one call: download, then apply it the way the release says - or, for a kind
 * this platform doesn't apply itself, open the release page for a manual install.
 *
 * One entry point on purpose: a package that has been downloaded but not applied is just a large
 * file on disk, and leaving that state reachable from the UI invites it. Resolves only if the app
 * is still here to read it - a successful install may already have closed it.
 */
export async function downloadAndInstall(update: AppUpdate, onProgress: (receivedBytes: number, totalBytes: number) => void): Promise<void> {
  const { appInstaller, app } = getPlatform();
  if (!appInstaller?.supports(update.kind)) {
    if (!isReleasePageUrl(update.releaseUrl)) throw new UpdateError("unreadable", "Unexpected release page in the update");
    logger.info("update", `${update.kind} update to ${update.version}: opening the release page`);
    await app.openExternal(update.releaseUrl);
    return;
  }
  const path = await downloadUpdate(update, onProgress);
  await installUpdate(path, update.version, update.kind);
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
    // Every platform's file is "hibiki-v<x.y.z>" followed by its own suffix.
    const match = /^hibiki-v(\d+(?:\.\d+)+)/i.exec(name);
    return match !== null && isNewerVersion(match[1], current);
  });
  if (!stillWanted) await files.remove(directory);
}
