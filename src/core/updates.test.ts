import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AppUpdate } from "@shared/types";
import type { FetchToFileRequest, Platform } from "../platform/types";
import { installPlatform } from "./platform";
import { checkForUpdate, cleanUpdateLeftovers, downloadAndInstall, downloadUpdate, installUpdate, UpdateError } from "./updates";

vi.mock("./logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const ROOT = "/data";
const DIR = `${ROOT}/updates`;
const BASE = "https://github.com/akkirrai1337/hibiki/releases/download/v3.1.0";
const HASH = "b".repeat(64);

/** Files as a map of path -> size; a directory is anything that is a prefix of a path. */
let disk: Map<string, number>;
let verdict: { ok: true } | { ok: false; reason: string };
let canInstall: boolean;
let installed: string[];
let transfer: (request: FetchToFileRequest) => Promise<void>;
let respond: (url: string) => { status: number; body: string };
let supported: string[];
let hashes: Map<string, string>;
let opened: string[];

function update(overrides: Partial<AppUpdate> = {}): AppUpdate {
  return {
    version: "3.1.0",
    releaseUrl: "https://github.com/akkirrai1337/hibiki/releases/tag/v3.1.0",
    downloadUrl: `${BASE}/hibiki-v3.1.0.apk`,
    fileName: "hibiki-v3.1.0.apk",
    sizeBytes: 1000,
    sha256: HASH,
    kind: "apk",
    notes: "",
    publishedAt: null,
    ...overrides,
  };
}

beforeEach(() => {
  disk = new Map();
  verdict = { ok: true };
  canInstall = true;
  installed = [];
  respond = () => ({ status: 200, body: "[]" });
  supported = ["apk"];
  hashes = new Map();
  opened = [];
  transfer = async (request) => {
    disk.set(request.filePath, 1000);
  };
  installPlatform({
    app: { version: () => "3.0.0", updateKey: () => "android", openExternal: async (url: string) => void opened.push(url) },
    paths: { userData: ROOT },
    http: { request: async (request: { url: string }) => ({ ...respond(request.url), url: request.url, headers: {} }) },
    downloads: {
      fetchToFile: async (request: FetchToFileRequest) => {
        await transfer(request);
        return { status: 200, headers: {}, bytesWritten: 0 };
      },
    },
    files: {
      join: (...parts: string[]) => parts.join("/"),
      stat: async (path: string) => (disk.has(path) ? { size: disk.get(path)!, mtimeMs: 0, isDirectory: false } : null),
      list: async (dir: string) => [...disk.keys()].filter((path) => path.startsWith(`${dir}/`)).map((path) => path.slice(dir.length + 1)),
      mkdir: async () => {},
      remove: async (path: string) => {
        for (const key of [...disk.keys()]) if (key === path || key.startsWith(`${path}/`)) disk.delete(key);
      },
      rename: async (from: string, to: string) => {
        disk.set(to, disk.get(from)!);
        disk.delete(from);
      },
      sha256: async (path: string) => hashes.get(path) ?? HASH,
    },
    appInstaller: {
      supports: (kind: string) => supported.includes(kind),
      canInstall: async () => canInstall,
      requestPermission: async () => true,
      verify: async () => verdict,
      install: async (path: string, kind: string) => {
        installed.push(`${kind}:${path}`);
      },
    },
  } as unknown as Platform);
});

describe("checkForUpdate", () => {
  const manifestUrl = `${BASE}/update-manifest.json`;
  const apkAsset = { name: "hibiki-v3.1.0.apk", size: 1000, browser_download_url: `${BASE}/hibiki-v3.1.0.apk` };
  const manifestAsset = { name: "update-manifest.json", size: 200, browser_download_url: manifestUrl };
  const releaseOf = (tag: string, assets: object[]) => ({
    tag_name: tag,
    html_url: `https://github.com/akkirrai1337/hibiki/releases/tag/${tag}`,
    draft: false,
    prerelease: false,
    assets,
  });
  const manifest = (platforms: object) => JSON.stringify({ version: "3.1.0", platforms });
  const apkEntry = { file: "hibiki-v3.1.0.apk", size: 1000, sha256: HASH, kind: "apk" };

  /** GitHub answering the release list with `releases`, and the newest release's manifest with `manifestBody`. */
  function serve(releases: object[], manifestBody: string) {
    respond = (url) => (url === manifestUrl ? { status: 200, body: manifestBody } : { status: 200, body: JSON.stringify(releases) });
  }

  it("offers this platform's file from a newer release's manifest", async () => {
    serve([releaseOf("v3.1.0", [apkAsset, manifestAsset])], manifest({ android: apkEntry }));
    const found = await checkForUpdate();
    expect(found?.fileName).toBe("hibiki-v3.1.0.apk");
    expect(found?.kind).toBe("apk");
    expect(found?.sha256).toBe(HASH);
  });

  it("offers a kind the platform cannot apply itself as a manual one", async () => {
    serve([releaseOf("v3.1.0", [apkAsset, manifestAsset])], manifest({ android: { ...apkEntry, kind: "replace" } }));
    expect((await checkForUpdate())?.kind).toBe("manual");
  });

  it("asks for the manifest as a plain download, without the GitHub API's Accept header", async () => {
    const seen: Array<{ url: string; headers?: Record<string, string> }> = [];
    const http = (await import("./platform")).getPlatform().http;
    const original = http.request;
    http.request = async (request) => {
      seen.push(request);
      return original(request);
    };
    serve([releaseOf("v3.1.0", [apkAsset, manifestAsset])], manifest({ android: apkEntry }));
    await checkForUpdate();
    expect(seen.find((r) => r.url === manifestUrl)?.headers).not.toHaveProperty("Accept");
    expect(seen.find((r) => r.url !== manifestUrl)?.headers).toHaveProperty("Accept");
  });

  it("offers nothing when the manifest has no entry for this platform", async () => {
    serve([releaseOf("v3.1.0", [apkAsset, manifestAsset])], manifest({ "win32-x64": { ...apkEntry, kind: "installer" } }));
    expect(await checkForUpdate()).toBeNull();
  });

  it("offers nothing for a release without a manifest", async () => {
    serve([releaseOf("v3.1.0", [apkAsset])], "");
    expect(await checkForUpdate()).toBeNull();
  });

  it("falls back to an older release when the newest has no build for this platform", async () => {
    const older = "https://github.com/akkirrai1337/hibiki/releases/download/v3.0.5";
    respond = (url) => {
      if (url === manifestUrl) return { status: 200, body: manifest({}) };
      if (url === `${older}/update-manifest.json`) {
        return { status: 200, body: JSON.stringify({ version: "3.0.5", platforms: { android: { ...apkEntry, file: "hibiki-v3.0.5.apk" } } }) };
      }
      return {
        status: 200,
        body: JSON.stringify([
          releaseOf("v3.1.0", [manifestAsset]),
          releaseOf("v3.0.5", [
            { name: "hibiki-v3.0.5.apk", size: 1000, browser_download_url: `${older}/hibiki-v3.0.5.apk` },
            { name: "update-manifest.json", size: 200, browser_download_url: `${older}/update-manifest.json` },
          ]),
        ]),
      };
    };
    expect((await checkForUpdate())?.version).toBe("3.0.5");
  });

  it("answers null rather than throwing when GitHub fails or sends nonsense", async () => {
    respond = () => ({ status: 403, body: "" });
    expect(await checkForUpdate()).toBeNull();
    respond = () => ({ status: 200, body: "<html>" });
    expect(await checkForUpdate()).toBeNull();
    serve([releaseOf("v3.1.0", [apkAsset, manifestAsset])], "<html>");
    expect(await checkForUpdate()).toBeNull();
  });
});

describe("downloadUpdate", () => {
  it("clears what an earlier run left and delivers the finished file only", async () => {
    disk.set(`${DIR}/hibiki-v2.0.0.apk`, 5);
    disk.set(`${DIR}/hibiki-v3.0.5.apk.part`, 2);
    const progress: number[] = [];
    transfer = async (request) => {
      expect(request.filePath).toBe(`${DIR}/hibiki-v3.1.0.apk.part`);
      expect(request.append).toBe(false);
      request.onProgress?.(500, 1000);
      disk.set(request.filePath, 1000);
    };
    const path = await downloadUpdate(update(), (received) => progress.push(received));
    expect(path).toBe(`${DIR}/hibiki-v3.1.0.apk`);
    expect([...disk.keys()]).toEqual([`${DIR}/hibiki-v3.1.0.apk`]);
    expect(progress).toEqual([500]);
  });

  it("refuses a file whose size is not the release's, and leaves nothing behind", async () => {
    transfer = async (request) => {
      disk.set(request.filePath, 400);
    };
    await expect(downloadUpdate(update(), () => {})).rejects.toMatchObject({ code: "download-failed" });
    expect(disk.size).toBe(0);
  });

  it("reports a failed transfer as download-failed and removes the partial file", async () => {
    transfer = async (request) => {
      disk.set(request.filePath, 10);
      throw new Error("HTTP 404");
    };
    await expect(downloadUpdate(update(), () => {})).rejects.toBeInstanceOf(UpdateError);
    expect(disk.size).toBe(0);
  });

  it("does not fetch again a package that is already complete", async () => {
    disk.set(`${DIR}/hibiki-v3.1.0.apk`, 1000);
    transfer = async () => {
      throw new Error("should not be called");
    };
    expect(await downloadUpdate(update(), () => {})).toBe(`${DIR}/hibiki-v3.1.0.apk`);
  });

  it("refuses an untrusted host and a file name that is a path", async () => {
    await expect(downloadUpdate(update({ downloadUrl: "https://evil.example.com/a.apk" }), () => {})).rejects.toMatchObject({ code: "download-failed" });
    await expect(downloadUpdate(update({ fileName: "../../x.apk" }), () => {})).rejects.toMatchObject({ code: "download-failed" });
  });

  it("refuses a file whose SHA-256 is not the release's, and leaves nothing behind", async () => {
    hashes.set(`${DIR}/hibiki-v3.1.0.apk.part`, "c".repeat(64));
    await expect(downloadUpdate(update(), () => {})).rejects.toMatchObject({ code: "download-failed" });
    expect(disk.size).toBe(0);
  });

  it("fetches again a file that is the right size but the wrong content", async () => {
    disk.set(`${DIR}/hibiki-v3.1.0.apk`, 1000);
    hashes.set(`${DIR}/hibiki-v3.1.0.apk`, "c".repeat(64));
    let fetched = false;
    transfer = async (request) => {
      fetched = true;
      disk.set(request.filePath, 1000);
    };
    await downloadUpdate(update(), () => {});
    expect(fetched).toBe(true);
  });
});

describe("installUpdate", () => {
  const file = `${DIR}/hibiki-v3.1.0.apk`;

  it("applies a package that checks out, the way its kind says", async () => {
    disk.set(file, 1000);
    await installUpdate(file, "3.1.0", "apk");
    expect(installed).toEqual([`apk:${file}`]);
  });

  it("deletes a package that fails verification and says why", async () => {
    disk.set(file, 1000);
    verdict = { ok: false, reason: "signature-mismatch" };
    await expect(installUpdate(file, "3.1.0", "apk")).rejects.toMatchObject({ code: "signature-mismatch" });
    expect(disk.has(file)).toBe(false);
    expect(installed).toEqual([]);
  });

  it("maps a reason it does not know to unreadable", async () => {
    verdict = { ok: false, reason: "something else" };
    await expect(installUpdate(file, "3.1.0", "apk")).rejects.toMatchObject({ code: "unreadable" });
  });

  it("keeps the package and asks for the permission when it is missing", async () => {
    disk.set(file, 1000);
    canInstall = false;
    await expect(installUpdate(file, "3.1.0", "apk")).rejects.toMatchObject({ code: "permission-needed" });
    expect(disk.has(file)).toBe(true);
    expect(installed).toEqual([]);
  });

  it("refuses a kind the platform does not apply instead of guessing", async () => {
    await expect(installUpdate(file, "3.1.0", "replace")).rejects.toMatchObject({ code: "unreadable" });
    expect(installed).toEqual([]);
  });
});

describe("downloadAndInstall", () => {
  it("downloads and applies a kind the platform supports", async () => {
    await downloadAndInstall(update(), () => {});
    expect(installed).toEqual([`apk:${DIR}/hibiki-v3.1.0.apk`]);
    expect(opened).toEqual([]);
  });

  it("sends a kind the platform cannot apply to the release page, downloading nothing", async () => {
    transfer = async () => {
      throw new Error("should not be called");
    };
    await downloadAndInstall(update({ kind: "replace" }), () => {});
    expect(opened).toEqual(["https://github.com/akkirrai1337/hibiki/releases/tag/v3.1.0"]);
    expect(installed).toEqual([]);
  });

  it("will not open a page that is not this project's release", async () => {
    await expect(downloadAndInstall(update({ kind: "manual", releaseUrl: "file:///C:/x.exe" }), () => {})).rejects.toMatchObject({ code: "unreadable" });
    expect(opened).toEqual([]);
  });
});

describe("cleanUpdateLeftovers", () => {
  it("removes a package the app has already caught up with", async () => {
    disk.set(`${DIR}/hibiki-v3.0.0.apk`, 1000);
    await cleanUpdateLeftovers();
    expect(disk.size).toBe(0);
  });

  it("keeps a newer package that is waiting to be installed", async () => {
    disk.set(`${DIR}/hibiki-v3.1.0.apk`, 1000);
    await cleanUpdateLeftovers();
    expect(disk.size).toBe(1);
  });

  it("reads the version out of any platform's file name", async () => {
    disk.set(`${DIR}/hibiki-v3.1.0-setup.exe`, 1000);
    await cleanUpdateLeftovers();
    expect(disk.size).toBe(1);
    disk.clear();
    disk.set(`${DIR}/hibiki-v3.0.0-setup.exe`, 1000);
    await cleanUpdateLeftovers();
    expect(disk.size).toBe(0);
  });
});
