import type { AppUpdate } from "./types";
import { compareVersions, isNewerVersion, parseVersion } from "./appVersion";

/** The subset of GitHub's release JSON this app reads. */
export interface GitHubReleaseAsset {
  name: string;
  size: number;
  browser_download_url: string;
}

export interface GitHubRelease {
  tag_name: string;
  html_url: string;
  body?: string | null;
  draft: boolean;
  prerelease: boolean;
  published_at?: string | null;
  assets: GitHubReleaseAsset[];
}

/** Where releases are listed. One place, so every platform's check looks at the same repository. */
export const RELEASES_URL = "https://api.github.com/repos/akkirrai1337/hibiki/releases?per_page=20";

/**
 * How a platform applies a downloaded build - what the release says, not what the client assumes:
 * - installer: run it and quit (Windows setup.exe)
 * - apk: hand it to the system package installer (Android)
 * - replace: swap the running app's files for the new ones and relaunch (macOS .app, Linux AppImage)
 * - manual: the person installs it from the release page themselves
 */
export type UpdateKind = "installer" | "apk" | "replace" | "manual";

export const UPDATE_KINDS: readonly UpdateKind[] = ["installer", "apk", "replace", "manual"];

/** The release asset that says which file each platform takes. Written by CI (scripts/make-update-manifest.mjs). */
export const MANIFEST_NAME = "update-manifest.json";

export interface ManifestEntry {
  file: string;
  size: number;
  sha256: string;
  kind: UpdateKind;
}

export interface UpdateManifest {
  version: string;
  /** Keyed by `updateKey`: "win32-x64", "darwin-arm64", "linux-x64", "android". */
  platforms: Record<string, ManifestEntry>;
}

/** The manifest key of the Electron build running on `platform`/`arch` (process.platform/arch). */
export function desktopUpdateKey(platform: string, arch: string): string {
  return `${platform}-${arch}`;
}

/**
 * The manifest, or null when it is not one. Entries that don't check out are dropped one by one
 * rather than failing the whole file: a bad line for a platform this build isn't on must not cost
 * it its own update.
 */
export function parseManifest(raw: unknown): UpdateManifest | null {
  if (typeof raw !== "object" || raw === null) return null;
  const { version, platforms } = raw as { version?: unknown; platforms?: unknown };
  if (typeof version !== "string" || typeof platforms !== "object" || platforms === null) return null;

  const valid: Record<string, ManifestEntry> = {};
  for (const [key, value] of Object.entries(platforms)) {
    const entry = value as Partial<ManifestEntry> | null;
    if (
      entry &&
      typeof entry.file === "string" && /^[\w.-]+$/.test(entry.file) &&
      typeof entry.size === "number" && Number.isInteger(entry.size) && entry.size > 0 &&
      typeof entry.sha256 === "string" && /^[0-9a-f]{64}$/i.test(entry.sha256) &&
      UPDATE_KINDS.includes(entry.kind as UpdateKind)
    ) {
      valid[key] = { file: entry.file, size: entry.size, sha256: entry.sha256.toLowerCase(), kind: entry.kind as UpdateKind };
    }
  }
  return { version, platforms: valid };
}

/** Only ever download from GitHub's own hosts. The release document is fetched over HTTPS from
 * this project's repository, so this should never fire - but its download URL is the one field
 * that turns into a file execution, and checking costs nothing. */
export function isTrustedDownloadUrl(url: string): boolean {
  try {
    const { protocol, hostname } = new URL(url);
    return (
      protocol === "https:" &&
      (hostname === "github.com" || hostname.endsWith(".github.com") || hostname === "objects.githubusercontent.com")
    );
  } catch {
    return false;
  }
}

/** This project's own release pages - the only thing "open the release" may be pointed at. */
export function isReleasePageUrl(url: string): boolean {
  return /^https:\/\/github\.com\/akkirrai1337\/hibiki\/releases\//.test(url);
}

export interface CandidateRelease {
  release: GitHubRelease;
  /** The tag without its leading "v". */
  version: string;
}

/**
 * The stable releases newer than the running build, highest version first.
 *
 * Pure, and separate from the fetching in core/updates.ts, so the rules below can be tested
 * against release shapes that don't exist yet - which is the whole difficulty here, since the
 * first real release is the one this code has to get right.
 *
 * Highest version, not the first listed: GitHub returns releases newest-published first, which
 * stops being the same thing the moment a patch to an older line is published after a newer
 * release - and taking the first would then offer that patch as an "update" to the newer build.
 */
export function newerReleases(releases: GitHubRelease[], currentVersion: string): CandidateRelease[] {
  if (!Array.isArray(releases)) return [];
  return releases
    .filter((release) => !release.draft && !release.prerelease)
    .map((release) => ({ release, version: String(release.tag_name ?? "").replace(/^v/i, "") }))
    .filter(({ version }) => isNewerVersion(version, currentVersion))
    .map((candidate) => ({ candidate, parsed: parseVersion(candidate.version) }))
    .filter((entry): entry is { candidate: CandidateRelease; parsed: NonNullable<ReturnType<typeof parseVersion>> } => entry.parsed !== null)
    .sort((a, b) => compareVersions(b.parsed, a.parsed))
    .map((entry) => entry.candidate);
}

/** The release's manifest asset, if it has one and it is hosted where it should be. */
export function manifestAssetOf(release: GitHubRelease): GitHubReleaseAsset | null {
  const asset = release.assets?.find((a) => a.name === MANIFEST_NAME);
  return asset && isTrustedDownloadUrl(asset.browser_download_url) ? asset : null;
}

/**
 * What this platform downloads from `candidate`, or null when the release has nothing for it - a
 * release cut before this platform's build was ready, or one whose upload didn't finish.
 */
export function buildUpdate(candidate: CandidateRelease, manifest: UpdateManifest, key: string): AppUpdate | null {
  const { release, version } = candidate;
  // A manifest that describes some other version belongs to some other release.
  if (manifest.version.replace(/^v/i, "") !== version) return null;
  const entry = manifest.platforms[key];
  if (!entry) return null;
  const asset = release.assets?.find((a) => a.name === entry.file);
  // The size GitHub reports for the upload against the one CI recorded: a mismatch is a broken upload.
  if (!asset || asset.size !== entry.size || !isTrustedDownloadUrl(asset.browser_download_url)) return null;
  return {
    version,
    releaseUrl: release.html_url,
    downloadUrl: asset.browser_download_url,
    fileName: asset.name,
    sizeBytes: asset.size,
    sha256: entry.sha256,
    kind: entry.kind,
    notes: release.body ?? "",
    publishedAt: release.published_at ?? null,
  };
}
