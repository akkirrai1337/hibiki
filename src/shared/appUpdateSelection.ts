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

/** Which build of a release a platform installs: the Windows installer or the Android package. */
export type UpdateTarget = "windows" | "android";

const ASSET_EXTENSION: Record<UpdateTarget, string> = { windows: ".exe", android: ".apk" };

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

/**
 * The release the running build should be offered, or null.
 *
 * Pure, and separate from the fetching in main/appUpdates.ts, so the rules below can be tested
 * against release shapes that don't exist yet - which is the whole difficulty here, since the
 * first real release is the one this code has to get right.
 */
export function selectUpdate(releases: GitHubRelease[], currentVersion: string, target: UpdateTarget = "windows"): AppUpdate | null {
  if (!Array.isArray(releases)) return null;

  const candidates = releases
    .filter((release) => !release.draft && !release.prerelease)
    .map((release) => {
      const version = String(release.tag_name ?? "").replace(/^v/i, "");
      if (!isNewerVersion(version, currentVersion)) return null;
      const parsed = parseVersion(version);
      if (!parsed) return null;
      // The installer, not the .blockmap or latest.yml electron-builder uploads beside it.
      const extension = ASSET_EXTENSION[target];
      const asset = release.assets?.find((a) => a.name.toLowerCase().endsWith(extension));
      if (!asset || !isTrustedDownloadUrl(asset.browser_download_url)) return null;
      return {
        parsed,
        update: {
          version,
          releaseUrl: release.html_url,
          downloadUrl: asset.browser_download_url,
          fileName: asset.name,
          sizeBytes: asset.size,
          notes: release.body ?? "",
          publishedAt: release.published_at ?? null,
        } satisfies AppUpdate,
      };
    })
    .filter((entry): entry is NonNullable<typeof entry> => entry !== null);

  if (candidates.length === 0) return null;
  // Highest version, not the first listed. GitHub returns releases newest-published first, which
  // stops being the same thing the moment a patch to an older line is published after a newer
  // release - and taking the first would then offer that patch as an "update" to the newer build.
  return candidates.reduce((a, b) => (compareVersions(a.parsed, b.parsed) >= 0 ? a : b)).update;
}
