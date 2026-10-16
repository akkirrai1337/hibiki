import { beforeEach, describe, expect, it, vi } from "vitest";
import type { HttpRequest, HttpResponse, Platform } from "../../platform/types";
import { installPlatform } from "../platform";
import { clearNetFetchCache, performNetFetch } from "./netFetch";
import { cachePolicy, clearHttpCache } from "./httpCache";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const files = new Map<string, string>();
let handler: (request: HttpRequest) => Promise<HttpResponse>;
const requests: HttpRequest[] = [];

beforeEach(async () => {
  files.clear();
  requests.length = 0;
  installPlatform({
    paths: { userData: "/data" },
    files: {
      join: (...parts: string[]) => parts.join("/"),
      mkdir: async () => {},
      readText: async (p: string) => { if (!files.has(p)) throw new Error("ENOENT"); return files.get(p)!; },
      writeText: async (p: string, t: string) => { files.set(p, t); },
      remove: async (p: string) => { for (const k of [...files.keys()]) if (k === p || k.startsWith(`${p}/`)) files.delete(k); },
    },
    http: { request: (request: HttpRequest) => { requests.push(request); return handler(request); } },
  } as unknown as Platform);
  await clearHttpCache();
  clearNetFetchCache();
});

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("disk revalidation", () => {
  it("asks with If-None-Match next time and reuses the body on 304", async () => {
    handler = async () => ({ status: 200, url: "", headers: { etag: ['"v1"'] }, body: "big page" });
    expect((await performNetFetch("https://s.example/list")).body).toBe("big page");
    await flush();
    clearNetFetchCache(); // past the 60s memory cache
    handler = async (r) => ({ status: r.headers?.["If-None-Match"] === '"v1"' ? 304 : 200, url: "", headers: {}, body: "" });
    const second = await performNetFetch("https://s.example/list");
    expect(requests[1].headers?.["If-None-Match"]).toBe('"v1"');
    expect(second).toMatchObject({ status: 200, ok: true, body: "big page" });
  });

  it("downloads again when the page changed", async () => {
    handler = async () => ({ status: 200, url: "", headers: { "last-modified": ["Mon, 01 Jan 2026 00:00:00 GMT"] }, body: "old" });
    await performNetFetch("https://s.example/p");
    await flush();
    clearNetFetchCache();
    handler = async () => ({ status: 200, url: "", headers: {}, body: "new" });
    expect((await performNetFetch("https://s.example/p")).body).toBe("new");
    expect(requests[1].headers?.["If-Modified-Since"]).toBe("Mon, 01 Jan 2026 00:00:00 GMT");
  });

  it("serves a still-fresh response without a request", async () => {
    handler = async () => ({ status: 200, url: "", headers: { "cache-control": ["public, max-age=600"] }, body: "fresh" });
    await performNetFetch("https://s.example/f");
    await flush();
    clearNetFetchCache();
    expect((await performNetFetch("https://s.example/f")).body).toBe("fresh");
    expect(requests).toHaveLength(1);
  });

  it("keeps nothing marked no-store that was sent with cookies, and no Set-Cookie on disk", async () => {
    handler = async () => ({ status: 200, url: "", headers: { etag: ['"x"'], "cache-control": ["no-store"] }, body: "secret" });
    await performNetFetch("https://s.example/ns", { headers: { Cookie: "sid=me" } });
    handler = async () => ({ status: 200, url: "", headers: { etag: ['"y"'], "set-cookie": ["sid=1"] }, body: "page" });
    await performNetFetch("https://s.example/sc");
    await flush();
    const written = [...files.values()].join("\n");
    expect(written).not.toContain("secret");
    expect(written).not.toContain("sid=1");
  });
});

describe("no-store on a public page", () => {
  it("is kept, but always asked about", async () => {
    handler = async () => ({ status: 200, url: "", headers: { "last-modified": ["Mon, 01 Jan 2026 00:00:00 GMT"], "cache-control": ["no-store"] }, body: "catalog" });
    await performNetFetch("https://s.example/catalog");
    await flush();
    clearNetFetchCache();
    handler = async () => ({ status: 304, url: "", headers: {}, body: "" });
    expect((await performNetFetch("https://s.example/catalog")).body).toBe("catalog");
    expect(requests).toHaveLength(2);
  });
});

describe("cachePolicy", () => {
  it("reads Cache-Control", () => {
    expect(cachePolicy({ "Cache-Control": "no-store" })).toBeNull();
    expect(cachePolicy({ "Cache-Control": "no-store" }, false)).toBe(0);
    expect(cachePolicy({ "cache-control": "no-cache" })).toBe(0);
    expect(cachePolicy({ "cache-control": "max-age=120", age: "20" })).toBe(100_000);
    expect(cachePolicy({ "cache-control": "max-age=86400" })).toBe(3_600_000);
    expect(cachePolicy({})).toBe(0);
  });
});
