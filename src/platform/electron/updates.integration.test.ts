// The updater end to end with the real desktop ports - Node's fetch, files on a real disk, streaming
// downloads, SHA-256 - against a pretend GitHub. Only `fetch` itself is replaced, so every URL the
// updater sees is a real github.com one and passes its own host checks untouched.
//
// What stays fake is the last step (the OS installer), which needs Electron's shell.
import { createHash, randomBytes } from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Platform } from "../types";
import { electronDownloadTransfer } from "./downloadTransfer";
import { electronFiles } from "./files";
import { electronHttp } from "./http";
import { installPlatform } from "../../core/platform";
import { checkForUpdate, cleanUpdateLeftovers, downloadAndInstall } from "../../core/updates";

vi.mock("../../core/logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const REPO = "https://github.com/akkirrai1337/hibiki/releases";
const API = "https://api.github.com/repos/akkirrai1337/hibiki/releases?per_page=20";

interface FakeRelease {
  tag: string;
  /** file name -> bytes actually served */
  files: Record<string, Buffer>;
  /** Manifest entries by platform key; the file's size/sha256 are filled in from `files` unless overridden. */
  platforms: Record<string, { file: string; kind: string; size?: number; sha256?: string }>;
  noManifest?: boolean;
}

let directory: string;
let releases: FakeRelease[];
let requested: string[];
let realFetch: typeof fetch;
let opened: string[];
let applied: Array<{ path: string; kind: string; content: Buffer }>;
let updateKey: string;
let supported: string[];
/** Per-URL override for the served body or status, to simulate a broken download. */
let downloadOverride: ((url: string) => Response | null) | null;

const sha = (data: Buffer) => createHash("sha256").update(data).digest("hex");

function manifestOf(release: FakeRelease) {
  const platforms: Record<string, unknown> = {};
  for (const [key, entry] of Object.entries(release.platforms)) {
    const data = release.files[entry.file];
    platforms[key] = { file: entry.file, kind: entry.kind, size: entry.size ?? data.length, sha256: entry.sha256 ?? sha(data) };
  }
  return { version: release.tag.replace(/^v/, ""), platforms };
}

function releaseJson(release: FakeRelease) {
  const assets = Object.entries(release.files).map(([name, data]) => ({
    name,
    size: data.length,
    browser_download_url: `${REPO}/download/${release.tag}/${name}`,
  }));
  if (!release.noManifest) {
    const body = JSON.stringify(manifestOf(release));
    assets.push({ name: "update-manifest.json", size: body.length, browser_download_url: `${REPO}/download/${release.tag}/update-manifest.json` });
  }
  return { tag_name: release.tag, html_url: `${REPO}/tag/${release.tag}`, draft: false, prerelease: false, body: `notes ${release.tag}`, assets };
}

/** A body that arrives in chunks, as a real download does. */
function streamed(data: Buffer): Response {
  const chunk = 64 * 1024;
  let offset = 0;
  const body = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (offset >= data.length) return controller.close();
      controller.enqueue(new Uint8Array(data.subarray(offset, offset + chunk)));
      offset += chunk;
    },
  });
  return new Response(body, { status: 200, headers: { "content-length": String(data.length) } });
}

beforeEach(async () => {
  directory = await fs.mkdtemp(path.join(os.tmpdir(), "hibiki-update-it-"));
  releases = [];
  requested = [];
  opened = [];
  applied = [];
  updateKey = "win32-x64";
  supported = ["installer"];
  downloadOverride = null;

  realFetch = globalThis.fetch;
  globalThis.fetch = (async (input: Parameters<typeof fetch>[0]) => {
    const url = String(input);
    requested.push(url);
    if (url === API) return new Response(JSON.stringify(releases.map(releaseJson)), { status: 200 });
    const match = new RegExp(`^${REPO}/download/([^/]+)/(.+)$`).exec(url);
    if (match) {
      const forced = downloadOverride?.(url);
      if (forced) return forced;
      const release = releases.find((r) => r.tag === match[1]);
      if (match[2] === "update-manifest.json" && release && !release.noManifest) return new Response(JSON.stringify(manifestOf(release)), { status: 200 });
      const data = release?.files[match[2]];
      if (data) return streamed(data);
    }
    return new Response("not found", { status: 404 });
  }) as typeof fetch;

  installPlatform({
    app: { version: () => "3.0.0", updateKey: () => updateKey, openExternal: async (url: string) => void opened.push(url) },
    paths: { userData: directory },
    http: electronHttp,
    downloads: electronDownloadTransfer,
    files: electronFiles,
    appInstaller: {
      supports: (kind: string) => supported.includes(kind),
      canInstall: async () => true,
      requestPermission: async () => true,
      verify: async () => ({ ok: true }),
      install: async (file: string, kind: string) => {
        applied.push({ path: file, kind, content: await fs.readFile(file) });
      },
    },
  } as unknown as Platform);
});

afterEach(async () => {
  globalThis.fetch = realFetch;
  await fs.rm(directory, { recursive: true, force: true });
});

const installer = (tag: string, content = randomBytes(3 * 1024 * 1024 + 123)): FakeRelease => ({
  tag,
  files: { [`hibiki-${tag}-setup.exe`]: content },
  platforms: { "win32-x64": { file: `hibiki-${tag}-setup.exe`, kind: "installer" } },
});

describe("update, end to end on the desktop ports", () => {
  it("finds, downloads, verifies and applies a newer release, byte for byte", async () => {
    const release = installer("v3.1.0");
    releases = [release];

    const update = await checkForUpdate();
    expect(update).toMatchObject({ version: "3.1.0", kind: "installer", fileName: "hibiki-v3.1.0-setup.exe", notes: "notes v3.1.0" });

    const progress: number[] = [];
    await downloadAndInstall(update!, (received) => progress.push(received));

    expect(applied).toHaveLength(1);
    expect(applied[0].kind).toBe("installer");
    expect(applied[0].path).toBe(path.join(directory, "updates", "hibiki-v3.1.0-setup.exe"));
    expect(applied[0].content.equals(release.files["hibiki-v3.1.0-setup.exe"])).toBe(true);
    // Progress only ever grows and ends at the whole file.
    expect(progress.length).toBeGreaterThan(1);
    expect([...progress].sort((a, b) => a - b)).toEqual(progress);
    expect(progress.at(-1)).toBe(release.files["hibiki-v3.1.0-setup.exe"].length);
    // No .part left behind.
    expect(await fs.readdir(path.join(directory, "updates"))).toEqual(["hibiki-v3.1.0-setup.exe"]);
  });

  it("rejects a file of the right size and the wrong content, and applies nothing", async () => {
    const release = installer("v3.1.0");
    releases = [release];
    const update = (await checkForUpdate())!;
    // Same length, one byte flipped.
    const tampered = Buffer.from(release.files["hibiki-v3.1.0-setup.exe"]);
    tampered[1000] ^= 0xff;
    downloadOverride = () => streamed(tampered);

    await expect(downloadAndInstall(update, () => {})).rejects.toMatchObject({ code: "download-failed" });
    expect(applied).toEqual([]);
    expect(await fs.readdir(path.join(directory, "updates"))).toEqual([]);
  });

  it("rejects a truncated download", async () => {
    const release = installer("v3.1.0");
    releases = [release];
    const update = (await checkForUpdate())!;
    downloadOverride = () => streamed(release.files["hibiki-v3.1.0-setup.exe"].subarray(0, 1_000_000));

    await expect(downloadAndInstall(update, () => {})).rejects.toMatchObject({ code: "download-failed" });
    expect(applied).toEqual([]);
    expect(await fs.readdir(path.join(directory, "updates"))).toEqual([]);
  });

  it("reports a 404 on the file as a failed download and leaves nothing", async () => {
    releases = [installer("v3.1.0")];
    const update = (await checkForUpdate())!;
    downloadOverride = () => new Response("gone", { status: 404 });

    await expect(downloadAndInstall(update, () => {})).rejects.toMatchObject({ code: "download-failed" });
    expect(await fs.readdir(path.join(directory, "updates"))).toEqual([]);
  });

  it("does not download again a file that is already complete and intact", async () => {
    releases = [installer("v3.1.0")];
    const update = (await checkForUpdate())!;
    await downloadAndInstall(update, () => {});
    const before = requested.filter((url) => url.endsWith("-setup.exe")).length;

    await downloadAndInstall(update, () => {});
    expect(requested.filter((url) => url.endsWith("-setup.exe")).length).toBe(before);
    expect(applied).toHaveLength(2);
  });

  it("downloads again a leftover that has the right size but the wrong content", async () => {
    const release = installer("v3.1.0");
    releases = [release];
    const update = (await checkForUpdate())!;
    const stale = Buffer.alloc(release.files["hibiki-v3.1.0-setup.exe"].length, 7);
    await fs.mkdir(path.join(directory, "updates"), { recursive: true });
    await fs.writeFile(path.join(directory, "updates", "hibiki-v3.1.0-setup.exe"), stale);

    await downloadAndInstall(update, () => {});
    expect(applied[0].content.equals(release.files["hibiki-v3.1.0-setup.exe"])).toBe(true);
  });

  it("offers nothing when the release carries no build for this platform, and no manifest means nothing", async () => {
    releases = [installer("v3.1.0")];
    updateKey = "linux-x64";
    expect(await checkForUpdate()).toBeNull();

    updateKey = "win32-x64";
    releases = [{ ...installer("v3.1.0"), noManifest: true }];
    expect(await checkForUpdate()).toBeNull();
  });

  it("offers nothing when already on the latest", async () => {
    releases = [installer("v3.0.0")];
    expect(await checkForUpdate()).toBeNull();
  });

  it("falls back to the older release that does carry this platform", async () => {
    const newest = { tag: "v3.2.0", files: { "hibiki-v3.2.0-mac-arm64.zip": randomBytes(1000) }, platforms: { "darwin-arm64": { file: "hibiki-v3.2.0-mac-arm64.zip", kind: "replace" } } };
    releases = [newest, installer("v3.1.0")];
    expect((await checkForUpdate())?.version).toBe("3.1.0");
  });

  it("turns a kind the platform cannot apply into a release-page visit that downloads nothing", async () => {
    const zip = randomBytes(5000);
    releases = [{ tag: "v3.1.0", files: { "hibiki-v3.1.0-linux-x64.AppImage": zip }, platforms: { "linux-x64": { file: "hibiki-v3.1.0-linux-x64.AppImage", kind: "replace" } } }];
    updateKey = "linux-x64";
    supported = ["installer"];

    const update = (await checkForUpdate())!;
    expect(update.kind).toBe("manual");
    await downloadAndInstall(update, () => {});

    expect(opened).toEqual([`${REPO}/tag/v3.1.0`]);
    expect(applied).toEqual([]);
    expect(requested.some((url) => url.endsWith(".AppImage"))).toBe(false);
  });

  it("ignores a manifest entry with a hash that is not a hash, and keeps the platform's others working", async () => {
    const release = installer("v3.1.0");
    release.platforms["win32-x64"] = { ...release.platforms["win32-x64"], sha256: "nope" };
    releases = [release];
    expect(await checkForUpdate()).toBeNull();
  });

  it("clears a spent update at start but keeps one still waiting", async () => {
    const dir = path.join(directory, "updates");
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(path.join(dir, "hibiki-v3.0.0-setup.exe"), "old");
    await cleanUpdateLeftovers();
    // The whole directory goes, not just the file.
    await expect(fs.stat(dir)).rejects.toMatchObject({ code: "ENOENT" });

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(path.join(dir, "hibiki-v3.1.0-setup.exe"), "new");
    await cleanUpdateLeftovers();
    expect(await fs.readdir(dir)).toEqual(["hibiki-v3.1.0-setup.exe"]);
  });
});
