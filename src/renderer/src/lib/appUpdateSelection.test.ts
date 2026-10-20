import { describe, expect, it } from "vitest";
import {
  buildUpdate,
  isReleasePageUrl,
  isTrustedDownloadUrl,
  manifestAssetOf,
  newerReleases,
  parseManifest,
  type GitHubRelease,
  type ManifestEntry,
  type UpdateManifest,
} from "@shared/appUpdateSelection";

const BASE = "https://github.com/akkirrai1337/hibiki/releases/download";
const HASH = "a".repeat(64);

function release(tag_name: string, overrides: Partial<GitHubRelease> = {}): GitHubRelease {
  return {
    tag_name,
    html_url: `https://github.com/akkirrai1337/hibiki/releases/tag/${tag_name}`,
    body: "",
    draft: false,
    prerelease: false,
    published_at: "2026-09-09T00:00:00Z",
    assets: [],
    ...overrides,
  };
}

function entry(file: string, kind: ManifestEntry["kind"], size = 1000): ManifestEntry {
  return { file, size, sha256: HASH, kind };
}

/** A release carrying a file per entry plus the manifest listing them. */
function published(tag: string, platforms: Record<string, ManifestEntry>): { release: GitHubRelease; manifest: UpdateManifest } {
  const assets = Object.values(platforms).map((e) => ({ name: e.file, size: e.size, browser_download_url: `${BASE}/${tag}/${e.file}` }));
  return { release: release(tag, { assets }), manifest: { version: tag.replace(/^v/, ""), platforms } };
}

describe("newerReleases", () => {
  it("lists releases newer than the running build, highest version first", () => {
    const releases = [release("v1.0.1"), release("v1.2.0"), release("v1.1.0"), release("v1.0.0")];
    expect(newerReleases(releases, "1.0.0").map((c) => c.version)).toEqual(["1.2.0", "1.1.0", "1.0.1"]);
  });

  it("offers nothing when the running build is current or ahead", () => {
    expect(newerReleases([release("v1.0.0")], "1.0.0")).toEqual([]);
    expect(newerReleases([release("v1.0.0")], "1.2.0")).toEqual([]);
    expect(newerReleases([], "1.0.0")).toEqual([]);
    expect(newerReleases("nonsense" as unknown as GitHubRelease[], "1.0.0")).toEqual([]);
  });

  // GitHub returns releases newest-published first, which stops matching "highest version" as soon
  // as a patch to an older line ships after a newer release.
  it("ranks by version, not by publication date", () => {
    const releases = [
      release("v1.0.1", { published_at: "2026-09-09T00:00:00Z" }),
      release("v1.2.0", { published_at: "2026-09-01T00:00:00Z" }),
    ];
    expect(newerReleases(releases, "1.0.0")[0].version).toBe("1.2.0");
  });

  it("ignores drafts and prereleases, and still finds the stable one behind them", () => {
    expect(newerReleases([release("v2.0.0", { draft: true })], "1.0.0")).toEqual([]);
    expect(newerReleases([release("v2.0.0", { prerelease: true })], "1.0.0")).toEqual([]);
    const mixed = [release("v2.0.0", { prerelease: true }), release("v1.1.0")];
    expect(newerReleases(mixed, "1.0.0").map((c) => c.version)).toEqual(["1.1.0"]);
  });

  it("survives a malformed tag rather than throwing mid-check", () => {
    const releases = [release("nightly"), release("v1.1.0")];
    expect(newerReleases(releases, "1.0.0").map((c) => c.version)).toEqual(["1.1.0"]);
  });
});

describe("buildUpdate", () => {
  const platforms = {
    "win32-x64": entry("hibiki-v1.1.0-setup.exe", "installer", 120_000_000),
    "darwin-arm64": entry("hibiki-v1.1.0-mac-arm64.zip", "replace", 110_000_000),
    "linux-x64": entry("hibiki-v1.1.0-linux-x64.AppImage", "replace", 105_000_000),
    android: entry("hibiki-v1.1.0.apk", "apk", 90_000_000),
  };
  const { release: rel, manifest } = published("v1.1.0", platforms);
  const candidate = { release: rel, version: "1.1.0" };

  it("takes each platform's own file from the same release", () => {
    for (const [key, expected] of Object.entries(platforms)) {
      const update = buildUpdate(candidate, manifest, key)!;
      expect(update.fileName).toBe(expected.file);
      expect(update.kind).toBe(expected.kind);
      expect(update.sizeBytes).toBe(expected.size);
      expect(update.sha256).toBe(HASH);
      expect(update.downloadUrl).toBe(`${BASE}/v1.1.0/${expected.file}`);
      expect(update.releaseUrl).toContain("/releases/tag/v1.1.0");
    }
  });

  it("offers nothing to a platform the release has no build for", () => {
    expect(buildUpdate(candidate, manifest, "freebsd-x64")).toBeNull();
    expect(buildUpdate(candidate, manifest, "linux-arm64")).toBeNull();
  });

  it("refuses a manifest that describes another version", () => {
    expect(buildUpdate(candidate, { ...manifest, version: "1.0.9" }, "android")).toBeNull();
  });

  it("refuses an entry whose file is not attached, or was uploaded at another size", () => {
    const missing = { ...manifest, platforms: { android: entry("hibiki-v1.1.0-other.apk", "apk") } };
    expect(buildUpdate(candidate, missing, "android")).toBeNull();
    const truncated = { ...manifest, platforms: { android: entry("hibiki-v1.1.0.apk", "apk", 5) } };
    expect(buildUpdate(candidate, truncated, "android")).toBeNull();
  });

  it("refuses a file hosted somewhere other than GitHub", () => {
    const offsite = release("v1.1.0", { assets: [{ name: "hibiki-v1.1.0.apk", size: 90_000_000, browser_download_url: "https://evil.example.com/hibiki.apk" }] });
    expect(buildUpdate({ release: offsite, version: "1.1.0" }, manifest, "android")).toBeNull();
  });
});

describe("parseManifest", () => {
  const good = { file: "hibiki-v1.1.0.apk", size: 10, sha256: HASH, kind: "apk" };

  it("reads a well-formed manifest", () => {
    expect(parseManifest({ version: "1.1.0", platforms: { android: good } })).toEqual({
      version: "1.1.0",
      platforms: { android: good },
    });
  });

  it("drops a bad entry without losing the good ones", () => {
    const parsed = parseManifest({
      version: "1.1.0",
      platforms: {
        android: good,
        pathy: { ...good, file: "../../x.apk" },
        shortHash: { ...good, sha256: "abc" },
        unknownKind: { ...good, kind: "rootkit" },
        empty: { ...good, size: 0 },
      },
    });
    expect(Object.keys(parsed!.platforms)).toEqual(["android"]);
  });

  it("answers null for anything that is not a manifest", () => {
    expect(parseManifest(null)).toBeNull();
    expect(parseManifest("x")).toBeNull();
    expect(parseManifest({ version: 1, platforms: {} })).toBeNull();
    expect(parseManifest({ version: "1.0.0" })).toBeNull();
  });
});

describe("manifestAssetOf", () => {
  const asset = (url: string) => ({ name: "update-manifest.json", size: 100, browser_download_url: url });

  it("finds the manifest among the release's files", () => {
    const rel = release("v1.1.0", { assets: [asset(`${BASE}/v1.1.0/update-manifest.json`)] });
    expect(manifestAssetOf(rel)?.size).toBe(100);
    expect(manifestAssetOf(release("v1.1.0"))).toBeNull();
  });

  it("does not trust one hosted somewhere other than GitHub", () => {
    expect(manifestAssetOf(release("v1.1.0", { assets: [asset("https://evil.example.com/update-manifest.json")] }))).toBeNull();
  });
});

describe("isTrustedDownloadUrl", () => {
  it("accepts the hosts GitHub actually serves release assets from", () => {
    expect(isTrustedDownloadUrl("https://github.com/o/r/releases/download/v1/a.exe")).toBe(true);
    expect(isTrustedDownloadUrl("https://objects.githubusercontent.com/x")).toBe(true);
  });

  it("rejects anything else, including a lookalike host and plain http", () => {
    expect(isTrustedDownloadUrl("http://github.com/o/r/a.exe")).toBe(false);
    expect(isTrustedDownloadUrl("https://github.com.evil.example/a.exe")).toBe(false);
    expect(isTrustedDownloadUrl("https://notgithub.com/a.exe")).toBe(false);
    expect(isTrustedDownloadUrl("file:///C:/evil.exe")).toBe(false);
    expect(isTrustedDownloadUrl("not a url")).toBe(false);
  });
});

describe("isReleasePageUrl", () => {
  it("accepts only this project's release pages", () => {
    expect(isReleasePageUrl("https://github.com/akkirrai1337/hibiki/releases/tag/v1.0.0")).toBe(true);
    expect(isReleasePageUrl("https://github.com/someone/else/releases/tag/v1.0.0")).toBe(false);
    expect(isReleasePageUrl("file:///C:/Windows/System32/calc.exe")).toBe(false);
  });
});
