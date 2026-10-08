import { beforeEach, describe, expect, it, vi } from "vitest";
import type { HttpRequest, HttpResponse, Platform } from "../../platform/types";
import { installPlatform } from "../platform";
import { clearNetFetchCache, performNetFetch, performNetFetchAll } from "./netFetch";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

let handler: (request: HttpRequest) => Promise<HttpResponse>;
const requests: HttpRequest[] = [];

function respond(body: string, extra: Partial<HttpResponse> = {}): HttpResponse {
  return { status: 200, url: "https://example.org/", headers: {}, body, ...extra };
}

beforeEach(() => {
  clearNetFetchCache();
  requests.length = 0;
  handler = async () => respond("ok");
  installPlatform({
    http: {
      request: (request: HttpRequest) => {
        requests.push(request);
        return handler(request);
      },
    },
  } as unknown as Platform);
});

describe("performNetFetch", () => {
  it("adds browser-like defaults without overriding what the extension set", async () => {
    await performNetFetch("https://example.org/a", { headers: { "user-agent": "Pinned/1.0", Referer: "https://r/" } });
    expect(requests[0].headers).toEqual({
      "user-agent": "Pinned/1.0",
      Referer: "https://r/",
      "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    });
    expect(requests[0].method).toBe("GET");
    expect(requests[0].redirect).toBe("follow");
    expect(requests[0].timeoutMs).toBe(12_000);
  });

  it("joins headers into one string each, set-cookie included", async () => {
    handler = async () => respond("ok", { headers: { "content-type": ["text/html"], "set-cookie": ["a=1; Path=/", "b=2"] } });
    const result = await performNetFetch("https://example.org/cookies");
    expect(result.headers).toEqual({ "content-type": "text/html", "set-cookie": "a=1; Path=/, b=2" });
    expect(result.ok).toBe(true);
  });

  it("serves a repeated GET from the short cache and joins one in flight", async () => {
    const [first, second] = await Promise.all([performNetFetch("https://example.org/same"), performNetFetch("https://example.org/same")]);
    const third = await performNetFetch("https://example.org/same");
    expect(requests).toHaveLength(1);
    expect([first.body, second.body, third.body]).toEqual(["ok", "ok", "ok"]);
  });

  it("never caches a POST or a failed response", async () => {
    await performNetFetch("https://example.org/p", { method: "post", body: "x" });
    await performNetFetch("https://example.org/p", { method: "post", body: "x" });
    handler = async () => respond("nope", { status: 500 });
    await performNetFetch("https://example.org/err");
    await performNetFetch("https://example.org/err");
    expect(requests.map((r) => r.method)).toEqual(["POST", "POST", "GET", "GET"]);
  });

  it("retries once after a transport failure, never after an HTTP status", async () => {
    let calls = 0;
    handler = async () => {
      calls += 1;
      if (calls === 1) throw Object.assign(new Error("This operation was aborted"), { name: "AbortError" });
      return respond("second try");
    };
    expect((await performNetFetch("https://example.org/flaky")).body).toBe("second try");
    expect(calls).toBe(2);

    calls = 0;
    handler = async () => {
      calls += 1;
      return respond("gone", { status: 404 });
    };
    expect((await performNetFetch("https://example.org/missing")).status).toBe(404);
    expect(calls).toBe(1);
  });

  it("gives up after the retry and rethrows the transport error", async () => {
    handler = async () => {
      throw new Error("fetch failed");
    };
    await expect(performNetFetch("https://example.org/down")).rejects.toThrow("fetch failed");
    expect(requests).toHaveLength(2);
  });
});

describe("performNetFetchAll", () => {
  it("answers in order and reports a dead request instead of failing the batch", async () => {
    handler = async (request) => {
      if (request.url.endsWith("/bad")) throw new Error("socket hang up");
      return respond(request.url.split("/").pop() ?? "");
    };
    const results = await performNetFetchAll([{ url: "https://example.org/one" }, { url: "https://example.org/bad" }, { url: "https://example.org/three" }]);
    expect(results.map((r) => r.body)).toEqual(["one", "", "three"]);
    expect(results[1]).toMatchObject({ status: 0, ok: false, error: "socket hang up" });
  });
});
