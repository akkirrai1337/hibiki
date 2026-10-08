import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AppUpdate } from "@shared/types";
import type { FetchToFileRequest, Platform } from "../platform/types";
import { installPlatform } from "./platform";
import { checkForUpdate, cleanUpdateLeftovers, downloadUpdate, installUpdate, UpdateError } from "./updates";

vi.mock("./logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const ROOT = "/data";
const DIR = `${ROOT}/updates`;

/** Files as a map of path -> size; a directory is anything that is a prefix of a path. */
let disk: Map<string, number>;
let verdict: { ok: true } | { ok: false; reason: string };
let canInstall: boolean;
let installed: string[];
let transfer: (request: FetchToFileRequest) => Promise<void>;
let httpStatus: number;
let httpBody: string;

function update(overrides: Partial<AppUpdate> = {}): AppUpdate {
  return {
    version: "3.1.0",
    releaseUrl: "https://github.com/akkirrai1337/hibiki/releases/tag/v3.1.0",
    downloadUrl: "https://github.com/akkirrai1337/hibiki/releases/download/v3.1.0/hibiki-v3.1.0.apk",
    fileName: "hibiki-v3.1.0.apk",
    sizeBytes: 1000,
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
  httpStatus = 200;
  httpBody = "[]";
  transfer = async (request) => {
    disk.set(request.filePath, 1000);
  };
  installPlatform({
    app: { version: () => "3.0.0" },
    paths: { userData: ROOT },
    http: { request: async () => ({ status: httpStatus, url: "", headers: {}, body: httpBody }) },
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
    },
    appInstaller: {
      canInstall: async () => canInstall,
      requestPermission: async () => true,
      verify: async () => verdict,
      install: async (path: string) => {
        installed.push(path);
      },
    },
  } as unknown as Platform);
});

describe("checkForUpdate", () => {
  it("offers the package of a newer release", async () => {
    httpBody = JSON.stringify([
      {
        tag_name: "v3.1.0",
        html_url: "https://github.com/akkirrai1337/hibiki/releases/tag/v3.1.0",
        draft: false,
        prerelease: false,
        assets: [{ name: "hibiki-v3.1.0.apk", size: 1000, browser_download_url: "https://github.com/akkirrai1337/hibiki/releases/download/v3.1.0/hibiki-v3.1.0.apk" }],
      },
    ]);
    expect((await checkForUpdate("android"))?.fileName).toBe("hibiki-v3.1.0.apk");
  });

  it("answers null rather than throwing when GitHub fails or sends nonsense", async () => {
    httpStatus = 403;
    expect(await checkForUpdate("android")).toBeNull();
    httpStatus = 200;
    httpBody = "<html>";
    expect(await checkForUpdate("android")).toBeNull();
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
});

describe("installUpdate", () => {
  const file = `${DIR}/hibiki-v3.1.0.apk`;

  it("opens the installer for a package that checks out", async () => {
    disk.set(file, 1000);
    await installUpdate(file, "3.1.0");
    expect(installed).toEqual([file]);
  });

  it("deletes a package that fails verification and says why", async () => {
    disk.set(file, 1000);
    verdict = { ok: false, reason: "signature-mismatch" };
    await expect(installUpdate(file, "3.1.0")).rejects.toMatchObject({ code: "signature-mismatch" });
    expect(disk.has(file)).toBe(false);
    expect(installed).toEqual([]);
  });

  it("maps a reason it does not know to unreadable", async () => {
    verdict = { ok: false, reason: "something else" };
    await expect(installUpdate(file, "3.1.0")).rejects.toMatchObject({ code: "unreadable" });
  });

  it("keeps the package and asks for the permission when it is missing", async () => {
    disk.set(file, 1000);
    canInstall = false;
    await expect(installUpdate(file, "3.1.0")).rejects.toMatchObject({ code: "permission-needed" });
    expect(disk.has(file)).toBe(true);
    expect(installed).toEqual([]);
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
});
