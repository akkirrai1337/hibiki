// HLS downloads whose sound is a separate stream, over a real migrated database (node:sqlite, as in
// repositories.test.ts) and in-memory files.
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import { describe, expect, it, vi } from "vitest";
import * as schema from "../db/schema";
import { applyMigrations, type MigrationJournal } from "../db/migrate";
import { installPlatform } from "../platform";
import type { Platform } from "../../platform/types";
import type { DownloadProgress, PlayerLink } from "@shared/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import { createDownloadsApi } from "./downloads";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const migrationsDir = fileURLToPath(new URL("../db/migrations", import.meta.url));
const journal = JSON.parse(readFileSync(`${migrationsDir}/meta/_journal.json`, "utf-8")) as MigrationJournal;
const sqlByTag = Object.fromEntries(
  readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).map((name) => [name.replace(/\.sql$/, ""), readFileSync(`${migrationsDir}/${name}`, "utf-8")]),
);

const request = { sourceId: "src", animeId: "a1", groupId: "g1", episodeId: "e1", episodeNumber: 1, episodeLabel: "Episode 1", animeTitle: "Title" };

/** Downloads one episode whose link is `link`, with `remote` answering every URL; resolves with the files written. */
async function download(link: PlayerLink, remote: Record<string, string>) {
  const sqlite = new DatabaseSync(":memory:");
  const values = (sql: string, params: unknown[]) => sqlite.prepare(sql).all(...(params as SQLInputValue[])).map((row) => Object.values(row));
  const db = drizzle(async (sql, params, method) => {
    if (method === "run") {
      sqlite.prepare(sql).run(...(params as SQLInputValue[]));
      return { rows: [] };
    }
    const rows = values(sql, params);
    return { rows: method === "get" ? (rows[0] as unknown as unknown[]) : rows };
  }, { schema });
  await applyMigrations(journal, sqlByTag, {
    exec: async (sql) => void sqlite.exec(sql),
    values: async (sql) => values(sql, []),
  }, async (text) => createHash("sha256").update(text).digest("hex"));

  const files = new Map<string, string>();
  const requested: { url: string; headers?: Record<string, string> }[] = [];
  const body = (url: string) => {
    if (!(url in remote)) throw new Error(`HTTP 404`);
    return remote[url];
  };
  let finished!: (progress: DownloadProgress) => void;
  const done = new Promise<DownloadProgress>((resolve) => { finished = resolve; });
  installPlatform({
    db: { get: () => db },
    paths: { downloads: "dl" },
    files: {
      join: (...parts: string[]) => parts.join("/"),
      mkdir: async () => {},
      stat: async (path: string) => (files.has(path) ? { size: files.get(path)!.length } : null),
      writeText: async (path: string, text: string) => void files.set(path, text),
      remove: async (path: string) => void files.delete(path),
    },
    http: { request: async ({ url }: { url: string }) => ({ status: 200, body: body(url) }) },
    downloads: {
      fetchToFile: async ({ url, headers, filePath, append }: { url: string; headers?: Record<string, string>; filePath: string; append: boolean }) => {
        requested.push({ url, headers });
        const data = body(url);
        files.set(filePath, (append ? files.get(filePath) ?? "" : "") + data);
        return { status: 200, headers: {}, bytesWritten: data.length };
      },
    },
    events: { emit: (_channel: string, progress: DownloadProgress) => { if (["done", "error", "unsupported"].includes(progress.status)) finished(progress); } },
  } as unknown as Platform);

  const runtime = {
    getPlayerLinks: async () => [link],
    getById: async () => null,
    getPlaybackGroups: async () => [],
  } as unknown as ExtensionRuntime;
  const api = createDownloadsApi(runtime);
  await api.start(request);
  const result = await done;
  return { result, files, requested, api };
}

describe("HLS downloads with separate audio", () => {
  it("save the link's audio stream into a file of its own and record it", async () => {
    const link: PlayerLink = {
      url: "https://cdn.test/v/video.m3u8",
      type: "DIRECT_HLS",
      headers: { Referer: "https://site.test/" },
      audioUrl: "https://cdn.test/a/audio.m3u8",
      audioHeaders: { Referer: "https://audio.test/" },
    };
    const { result, files, requested, api } = await download(link, {
      "https://cdn.test/v/video.m3u8": "#EXTM3U\n#EXTINF:4,\nv1.ts\n#EXTINF:3,\nv2.ts\n#EXT-X-ENDLIST\n",
      "https://cdn.test/v/v1.ts": "V1",
      "https://cdn.test/v/v2.ts": "V2",
      "https://cdn.test/a/audio.m3u8": "#EXTM3U\n#EXTINF:4,\na1.aac\n#EXTINF:3,\na2.aac\n#EXT-X-ENDLIST\n",
      "https://cdn.test/a/a1.aac": "A1",
      "https://cdn.test/a/a2.aac": "A2",
    });
    expect(result).toEqual({ episodeId: "e1", status: "done", filePath: "dl/Title/Episode 1.ts" });
    expect(files.get("dl/Title/Episode 1.ts")).toBe("V1V2");
    expect(files.get("dl/Title/Episode 1.audio.aac")).toBe("A1A2");
    expect(requested.find((r) => r.url.endsWith("a1.aac"))?.headers).toEqual({ Referer: "https://audio.test/" });
    expect(await api.getForEpisode("src", "a1", "e1")).toEqual({
      filePath: "dl/Title/Episode 1.ts",
      durationMs: 7000,
      quality: null,
      subtitles: [],
      parts: { audio: "dl/Title/Episode 1.audio.aac" },
    });
    expect((await api.list())[0].fileSizeBytes).toBe(8);

    await api.remove("src", "a1", "e1");
    expect(files.size).toBe(0);
  });

  it("take the audio rendition of a master playlist and the init segments of fMP4 streams", async () => {
    const { result, files, api } = await download({ url: "https://cdn.test/master.m3u8", type: "DIRECT_HLS" }, {
      "https://cdn.test/master.m3u8": [
        "#EXTM3U",
        '#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",DEFAULT=NO,URI="audio/en.m3u8"',
        '#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Japanese",DEFAULT=YES,URI="audio/ja.m3u8"',
        '#EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="aud"',
        "video/720.m3u8",
        "",
      ].join("\n"),
      "https://cdn.test/video/720.m3u8": '#EXTM3U\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:5,\n1.m4s\n#EXTINF:5,\n2.m4s\n#EXT-X-ENDLIST\n',
      "https://cdn.test/video/init.mp4": "VI",
      "https://cdn.test/video/1.m4s": "V1",
      "https://cdn.test/video/2.m4s": "V2",
      "https://cdn.test/audio/ja.m3u8": '#EXTM3U\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:10,\n1.m4s\n#EXT-X-ENDLIST\n',
      "https://cdn.test/audio/init.mp4": "AI",
      "https://cdn.test/audio/1.m4s": "A1",
    });
    expect(result.status).toBe("done");
    expect(Object.fromEntries(files)).toEqual({
      "dl/Title/Episode 1.m4s": "V1V2",
      "dl/Title/Episode 1.init.mp4": "VI",
      "dl/Title/Episode 1.audio.m4s": "A1",
      "dl/Title/Episode 1.audio.init.mp4": "AI",
    });
    expect((await api.getForEpisode("src", "a1", "e1"))?.parts).toEqual({
      videoInit: "dl/Title/Episode 1.init.mp4",
      audio: "dl/Title/Episode 1.audio.m4s",
      audioInit: "dl/Title/Episode 1.audio.init.mp4",
    });
  });
});
