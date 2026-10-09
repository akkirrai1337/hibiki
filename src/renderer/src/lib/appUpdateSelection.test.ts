import { describe, expect, it } from "vitest";
import { isTrustedDownloadUrl, selectUpdate, type GitHubRelease } from "@shared/appUpdateSelection";

function release(overrides: Partial<GitHubRelease> & { tag_name: string }): GitHubRelease {
  const version = overrides.tag_name.replace(/^v/, "");
  return {
    html_url: `https://github.com/akkirrai1337/hibiki/releases/tag/${overrides.tag_name}`,
    body: "",
    draft: false,
    prerelease: false,
    published_at: "2026-09-09T00:00:00Z",
    assets: [
      {
        name: `hibiki-v${version}-setup.exe`,
        size: 120_000_000,
        browser_download_url: `https://github.com/akkirrai1337/hibiki/releases/download/${overrides.tag_name}/hibiki-v${version}-setup.exe`,
      },
    ],
    ...overrides,
  };
}

describe("selectUpdate", () => {
  it("offers a newer release and reports what it will download", () => {
    const update = selectUpdate([release({ tag_name: "v1.1.0" })], "1.0.0");
    expect(update).not.toBeNull();
    expect(update!.version).toBe("1.1.0");
    expect(update!.fileName).toBe("hibiki-v1.1.0-setup.exe");
    expect(update!.sizeBytes).toBe(120_000_000);
    expect(update!.releaseUrl).toContain("/releases/tag/v1.1.0");
  });

  it("offers nothing when the running build is current or ahead", () => {
    expect(selectUpdate([release({ tag_name: "v1.0.0" })], "1.0.0")).toBeNull();
    expect(selectUpdate([release({ tag_name: "v1.0.0" })], "1.2.0")).toBeNull();
    expect(selectUpdate([], "1.0.0")).toBeNull();
  });

  // GitHub returns releases newest-published first, which stops matching "highest version" as soon
  // as a patch to an older line ships after a newer release - taking the first would then offer
  // 1.0.1 to someone already past it.
  it("picks the highest version, not the most recently published", () => {
    const releases = [
      release({ tag_name: "v1.0.1", published_at: "2026-09-09T00:00:00Z" }),
      release({ tag_name: "v1.2.0", published_at: "2026-09-01T00:00:00Z" }),
    ];
    expect(selectUpdate(releases, "1.0.0")!.version).toBe("1.2.0");
  });

  it("ignores drafts and prereleases", () => {
    expect(selectUpdate([release({ tag_name: "v2.0.0", draft: true })], "1.0.0")).toBeNull();
    expect(selectUpdate([release({ tag_name: "v2.0.0", prerelease: true })], "1.0.0")).toBeNull();
    // ...and still finds the stable one behind them.
    const mixed = [release({ tag_name: "v2.0.0", prerelease: true }), release({ tag_name: "v1.1.0" })];
    expect(selectUpdate(mixed, "1.0.0")!.version).toBe("1.1.0");
  });

  it("skips a release with no installer attached", () => {
    // A release published before its upload finished, or one carrying only the .blockmap that
    // electron-builder writes alongside the installer.
    const noAssets = release({ tag_name: "v1.1.0", assets: [] });
    const onlyBlockmap = release({
      tag_name: "v1.1.0",
      assets: [{
        name: "hibiki-v1.1.0-setup.exe.blockmap",
        size: 130_000,
        browser_download_url: "https://github.com/akkirrai1337/hibiki/releases/download/v1.1.0/a.blockmap",
      }],
    });
    expect(selectUpdate([noAssets], "1.0.0")).toBeNull();
    expect(selectUpdate([onlyBlockmap], "1.0.0")).toBeNull();
  });

  it("refuses an asset hosted somewhere other than GitHub", () => {
    const offsite = release({
      tag_name: "v1.1.0",
      assets: [{ name: "hibiki-v1.1.0-setup.exe", size: 1, browser_download_url: "https://evil.example.com/setup.exe" }],
    });
    expect(selectUpdate([offsite], "1.0.0")).toBeNull();
  });

  it("survives a malformed tag rather than throwing mid-check", () => {
    const releases = [release({ tag_name: "nightly" }), release({ tag_name: "v1.1.0" })];
    expect(selectUpdate(releases, "1.0.0")!.version).toBe("1.1.0");
  });
});

describe("selectUpdate for Android", () => {
  const base = "https://github.com/akkirrai1337/hibiki/releases/download";
  const both = (tag: string, overrides: Partial<GitHubRelease> = {}) => {
    const version = tag.replace(/^v/, "");
    return release({
      tag_name: tag,
      assets: [
        { name: `hibiki-v${version}-setup.exe`, size: 120_000_000, browser_download_url: `${base}/${tag}/hibiki-v${version}-setup.exe` },
        { name: `hibiki-v${version}.apk`, size: 90_000_000, browser_download_url: `${base}/${tag}/hibiki-v${version}.apk` },
      ],
      ...overrides,
    });
  };

  it("takes the installer on Windows and the package on Android from the same release", () => {
    expect(selectUpdate([both("v1.1.0")], "1.0.0", "windows")!.fileName).toBe("hibiki-v1.1.0-setup.exe");
    const android = selectUpdate([both("v1.1.0")], "1.0.0", "android")!;
    expect(android.fileName).toBe("hibiki-v1.1.0.apk");
    expect(android.sizeBytes).toBe(90_000_000);
  });

  it("does not offer a release that has no package", () => {
    expect(selectUpdate([release({ tag_name: "v1.1.0" })], "1.0.0", "android")).toBeNull();
  });

  it("picks the highest version that carries a package", () => {
    const releases = [both("v1.0.1"), both("v1.2.0"), release({ tag_name: "v1.3.0" })];
    expect(selectUpdate(releases, "1.0.0", "android")!.version).toBe("1.2.0");
  });

  it("refuses a package hosted somewhere other than GitHub", () => {
    const offsite = release({
      tag_name: "v1.1.0",
      assets: [{ name: "hibiki-v1.1.0.apk", size: 1, browser_download_url: "https://evil.example.com/hibiki.apk" }],
    });
    expect(selectUpdate([offsite], "1.0.0", "android")).toBeNull();
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
