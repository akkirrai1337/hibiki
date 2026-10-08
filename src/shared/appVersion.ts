/**
 * Compares release tags like "v1.2.0", "1.10", "1.2.0-prerelease.3".
 *
 * Kept separate from the extension-version comparison in the renderer's lib/version.ts: that one
 * answers "does this source have an update", is fed basic x.y.z from manifests the repository
 * validates, and deliberately refuses anything it can't parse. This one is fed whatever a GitHub
 * release happens to be tagged, which includes the prerelease suffixes this project's own release
 * workflow produces.
 */
export interface ParsedVersion {
  numbers: number[];
  /** Empty for a stable release. Present for "1.0.0-prerelease.2" and similar. */
  prerelease: string;
}

export function parseVersion(raw: string): ParsedVersion | null {
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim().replace(/^v/i, "");
  if (trimmed === "") return null;
  const [core, ...rest] = trimmed.split("-");
  const numbers = core.split(".").map((part) => Number.parseInt(part, 10));
  if (numbers.length === 0 || numbers.some((n) => Number.isNaN(n))) return null;
  return { numbers, prerelease: rest.join("-") };
}

/**
 * Negative when `a` is older, positive when newer, 0 when equal.
 *
 * A prerelease sorts *below* the release it leads to, per semver - so 1.1.0-prerelease.2 is older
 * than 1.1.0. That matters here beyond tidiness: this project ships prereleases under the base
 * version (see .github/workflows/release.yml), and without this rule an installed 1.1.0
 * would be offered a "newer" 1.1.0-prerelease.1 and downgrade itself.
 */
export function compareVersions(a: ParsedVersion, b: ParsedVersion): number {
  const length = Math.max(a.numbers.length, b.numbers.length);
  for (let i = 0; i < length; i++) {
    const difference = (a.numbers[i] ?? 0) - (b.numbers[i] ?? 0);
    if (difference !== 0) return difference < 0 ? -1 : 1;
  }
  if (a.prerelease === b.prerelease) return 0;
  if (a.prerelease === "") return 1;
  if (b.prerelease === "") return -1;
  return a.prerelease < b.prerelease ? -1 : 1;
}

/** True when `candidate` is a release the app at `current` should be offered. */
export function isNewerVersion(candidate: string, current: string): boolean {
  const parsedCandidate = parseVersion(candidate);
  const parsedCurrent = parseVersion(current);
  if (!parsedCandidate || !parsedCurrent) return false;
  return compareVersions(parsedCandidate, parsedCurrent) > 0;
}
