import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, describe, expect, it } from "vitest";
import { ApkHostError, ApkHostProcess, apkIconUrl, apkSourcesPort, locateApkHost, type LocateOptions } from "./apkHost";

const options = (existing: string[], overrides: Partial<LocateOptions> = {}): LocateOptions => ({
  resourcesPath: null,
  appPath: path.join("/app"),
  platform: "linux",
  env: {},
  exists: (p) => existing.includes(p),
  ...overrides,
});

describe("locateApkHost", () => {
  it("uses the bundled jars and runtime of a packaged app", () => {
    const resources = path.join("/res");
    const bundled = path.join(resources, "apk-host");
    const found = locateApkHost(options([path.join(bundled, "lib"), path.join(bundled, "runtime", "bin", "java")], { resourcesPath: resources }));
    expect(found).toEqual({ java: path.join(bundled, "runtime", "bin", "java"), libDir: path.join(bundled, "lib") });
  });

  it("names java.exe on Windows", () => {
    const resources = path.join("/res");
    const bundled = path.join(resources, "apk-host");
    const java = path.join(bundled, "runtime", "bin", "java.exe");
    expect(locateApkHost(options([path.join(bundled, "lib"), java], { resourcesPath: resources, platform: "win32" }))?.java).toBe(java);
  });

  it("is off when a packaged app lacks its runtime, rather than falling back to whatever java is around", () => {
    const resources = path.join("/res");
    expect(locateApkHost(options([path.join(resources, "apk-host", "lib")], { resourcesPath: resources }))).toBeNull();
  });

  it("is off in development until the host has been built", () => {
    expect(locateApkHost(options([]))).toBeNull();
  });

  it("finds the Gradle build in development and prefers HIBIKI_JAVA, then JAVA_HOME, then the PATH", () => {
    const lib = path.join("/app", "apk-host", "build", "install", "apk-host", "lib");
    expect(locateApkHost(options([lib], { env: {} }))).toEqual({ java: "java", libDir: lib });
    const home = path.join("/jdk");
    const fromHome = path.join(home, "bin", "java");
    expect(locateApkHost(options([lib, fromHome], { env: { JAVA_HOME: home } }))?.java).toBe(fromHome);
    expect(locateApkHost(options([lib, fromHome, "/custom/java"], { env: { JAVA_HOME: home, HIBIKI_JAVA: "/custom/java" } }))?.java).toBe("/custom/java");
  });

  it("takes the host folder from HIBIKI_APK_HOST_DIR", () => {
    const lib = path.join("/elsewhere", "lib");
    expect(locateApkHost(options([lib], { env: { HIBIKI_APK_HOST_DIR: "/elsewhere" } }))?.libDir).toBe(lib);
  });
});

describe("apkIconUrl", () => {
  it("is addressed by package name, escaped", () => {
    expect(apkIconUrl("eu.kanade.tachiyomi.animeextension.ru.animevost")).toBe("hibiki-apk-image://icon/eu.kanade.tachiyomi.animeextension.ru.animevost");
    expect(apkIconUrl("a b")).toBe("hibiki-apk-image://icon/a%20b");
  });
});

describe("apkSourcesPort", () => {
  const calls: Array<{ method: string; route: string; body?: unknown }> = [];
  const host = {
    request: async (method: string, route: string, body?: unknown) => {
      calls.push({ method, route, body });
      if (route === "/extensions") {
        return {
          sources: [
            { id: "apk:1", name: "A", packageName: "pkg.a" },
            { id: "apk:2", name: "B", packageName: "pkg.b" },
          ],
          extensions: [{ packageName: "pkg.a", iconPath: "/x/pkg.a.png" }, { packageName: "pkg.b", iconPath: null }],
        };
      }
      return { items: [{ id: "x" }] };
    },
  } as unknown as ApkHostProcess;
  const port = apkSourcesPort(host);

  it("lists sources with an icon only where the extension has one", async () => {
    const list = await port.list();
    expect(list.map((source) => source.iconUrl)).toEqual([apkIconUrl("pkg.a"), null]);
  });

  it("turns the runtime's calls into the host's routes", async () => {
    calls.length = 0;
    await port.call("apk:5", "search", [{ query: "naruto" }]);
    await port.call("apk:5", "latest", [10]);
    await port.call("apk:5", "getById", ["/title/1"]);
    await port.call("apk:5", "getPlaybackGroups", ["/title/1"]);
    await port.call("apk:5", "getPlayerLinks", ["/title/1", "episodes", "/ep/3"]);
    await port.call("apk:5", "getSettings", []);
    await port.call("apk:5", "readSettings", []);
    await port.call("apk:5", "writeSetting", ["quality", "720"]);
    await port.call("apk:5", "writeSetting", ["quality", null]);
    expect(calls.map((call) => [call.route, call.body])).toEqual([
      ["/source/apk%3A5/search", { request: { query: "naruto" } }],
      ["/source/apk%3A5/latest", { limit: 10 }],
      ["/source/apk%3A5/getById", { id: "/title/1" }],
      ["/source/apk%3A5/playbackGroups", { titleId: "/title/1" }],
      ["/source/apk%3A5/playerLinks", { titleId: "/title/1", episodeId: "/ep/3" }],
      ["/source/apk%3A5/filterCatalog", {}],
      ["/source/apk%3A5/readSettings", {}],
      ["/source/apk%3A5/writeSetting", { key: "quality", value: "720" }],
      ["/source/apk%3A5/writeSetting", { key: "quality", value: null }],
    ]);
  });

  it("unwraps lists and refuses what it does not know", async () => {
    expect(await port.call("apk:5", "search", [{}])).toEqual([{ id: "x" }]);
    await expect(port.call("apk:5", "explode", [])).rejects.toThrow(/do not support/);
  });
});

// The real thing: starts the built host (apk-host/build/install) when there is one and a Java to run it.
const lib = path.join(process.cwd(), "apk-host", "build", "install", "apk-host", "lib");
const runnable = fs.existsSync(lib);

describe.skipIf(!runnable)("ApkHostProcess with the real host", () => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-apk-host-"));
  const host = new ApkHostProcess({ java: process.env.HIBIKI_JAVA ?? "java", libDir: lib }, dataDir);
  afterAll(() => {
    host.stop();
    fs.rmSync(dataDir, { recursive: true, force: true });
  });

  it("starts on first use and answers", async () => {
    const listing = await host.request<{ sources: unknown[]; extensions: unknown[] }>("GET", "/extensions");
    expect(listing).toEqual({ sources: [], extensions: [] });
  }, 60_000);

  it("reports a host error with its message", async () => {
    await expect(host.request("POST", "/source/apk%3A1/search", {})).rejects.toThrow(/not installed/);
    await expect(host.request("POST", "/install", { url: "http://insecure.example/x.apk" })).rejects.toThrow(ApkHostError);
  }, 30_000);

  it("starts again after the process died", async () => {
    (host as unknown as { child: { kill(): void } }).child.kill();
    await new Promise((resolve) => setTimeout(resolve, 500));
    const listing = await host.request<{ extensions: unknown[] }>("GET", "/extensions");
    expect(listing.extensions).toEqual([]);
  }, 60_000);
});
