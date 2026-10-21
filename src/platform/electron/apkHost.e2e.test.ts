// The whole path an APK source takes on the desktop, against the real repository and the real site:
// the Yuzono index the app seeds -> the marketplace's own parsing -> install through the port ->
// the source listed with its icon -> search -> a title -> episodes -> links. Live network, so it is off
// unless HIBIKI_E2E_APK=1 (and the host built: `gradlew installDist` in apk-host/).
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, describe, expect, it, vi } from "vitest";
import { ApkHostProcess, apkSourcesPort } from "./apkHost";

const lib = path.join(process.cwd(), "apk-host", "build", "install", "apk-host", "lib");
const enabled = process.env.HIBIKI_E2E_APK === "1" && fs.existsSync(lib);

// fetchRepositoryIndex asks the platform whether it runs APK sources before it will read an APK index.
vi.mock("../../core/platform", () => ({
  getPlatform: () => ({
    apkSources: {},
    http: {
      request: async ({ url }: { url: string }) => {
        const response = await fetch(url);
        return { status: response.status, headers: {}, body: await response.text() };
      },
    },
  }),
}));

describe.skipIf(!enabled)("an APK source from the Yuzono repository, end to end", () => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-apk-e2e-"));
  const host = new ApkHostProcess({ java: process.env.HIBIKI_JAVA ?? "java", libDir: lib }, dataDir);
  const port = apkSourcesPort(host);
  afterAll(async () => {
    await host.stop();
    fs.rmSync(dataDir, { recursive: true, force: true });
  });

  it("installs from the repository's own entry and answers a search", async () => {
    const { DEFAULT_APK_REPOSITORY_URL, fetchRepositoryIndex } = await import("../../core/marketplace");
    const index = await fetchRepositoryIndex(DEFAULT_APK_REPOSITORY_URL);
    const entry = index.find((extension) => extension.apkPackage?.endsWith(".animevost"));
    expect(entry, "Animevost is in the repository").toBeTruthy();

    await port.install(entry!.manifestUrl, entry!.apkPackage!);
    const sources = await port.list();
    const source = sources.find((candidate) => candidate.packageName === entry!.apkPackage);
    expect(source?.id).toBe(entry!.id);
    expect(source?.iconUrl).toMatch(/^hibiki-apk-image:\/\/icon\//);

    const found = (await port.call(source!.id, "search", [{ limit: 5 }])) as Array<{ id: string; title: string; posterUrl: string | null }>;
    expect(found.length).toBeGreaterThan(0);
    // A poster is addressed through the application's own scheme, so the source's headers and cookies apply.
    expect(found[0].posterUrl).toMatch(/^hibiki-apk-image:\/\/img\?s=/);

    const groups = (await port.call(source!.id, "getPlaybackGroups", [found[0].id])) as Array<{ episodes: Array<{ id: string }> }>;
    expect(groups[0].episodes.length).toBeGreaterThan(0);
    const links = (await port.call(source!.id, "getPlayerLinks", [found[0].id, "episodes", groups[0].episodes[0].id])) as unknown[];
    expect(links.length).toBeGreaterThan(0);
  }, 180_000);

  it("serves the poster the way the source fetches it", async () => {
    const [source] = await port.list();
    const found = (await port.call(source.id, "search", [{ limit: 1 }])) as Array<{ posterUrl: string }>;
    const query = new URL(found[0].posterUrl).search;
    const image = await host.fetchRaw(`/image${query}`);
    expect(image.status).toBe(200);
    expect(image.headers.get("content-type")).toMatch(/^image\//);
    expect((await image.arrayBuffer()).byteLength).toBeGreaterThan(500);
  }, 60_000);

  it("removes it again", async () => {
    const [source] = await port.list();
    await port.uninstall(source.packageName);
    expect(await port.list()).toEqual([]);
  }, 30_000);
});
