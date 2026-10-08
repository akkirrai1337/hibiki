import { beforeEach, describe, expect, it, vi } from "vitest";
import type { HttpRequest, HttpResponse, Platform } from "../../platform/types";
import { installPlatform } from "../platform";
import { challengeUrlInError, forgetClearance, isCloudflareChallenge, rememberClearance, solveChallenge, withClearance } from "./cloudflare";
import { clearNetFetchCache, performNetFetch } from "./netFetch";
import { cloudflareCheckError, cloudflareCheckOf } from "@shared/cloudflare";

vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const CHALLENGE_HTML = `<!DOCTYPE html><html><head><title>Just a moment...</title></head><body><script>window._cf_chl_opt={cvId:'3'}</script></body></html>`;
const session = { cookies: { cf_clearance: "abc" }, cookieHeader: "cf_clearance=abc", userAgent: "Browser/1.0" };

describe("isCloudflareChallenge", () => {
  it("trusts Cloudflare's own cf-mitigated header", () => {
    expect(isCloudflareChallenge(403, { "cf-mitigated": "challenge" }, "")).toBe(true);
  });

  it("recognises the interstitial by its markup on a Cloudflare error status", () => {
    expect(isCloudflareChallenge(403, { server: "cloudflare" }, CHALLENGE_HTML)).toBe(true);
    expect(isCloudflareChallenge(503, { Server: "cloudflare" }, CHALLENGE_HTML)).toBe(true);
  });

  it("leaves ordinary pages, other servers and firewall blocks alone", () => {
    // A normal page behind Cloudflare loads its challenge-platform script too.
    expect(isCloudflareChallenge(200, { server: "cloudflare" }, `<script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>`)).toBe(false);
    expect(isCloudflareChallenge(403, { server: "nginx" }, CHALLENGE_HTML)).toBe(false);
    expect(isCloudflareChallenge(403, { server: "cloudflare" }, `<title>Attention Required! | Cloudflare</title><h1>Sorry, you have been blocked</h1>`)).toBe(false);
  });

  it("reads the page out of an APK source's give-up message", () => {
    expect(challengeUrlInError("java.io.IOException: Cloudflare challenge could not be solved for https://a.example/x")).toBe("https://a.example/x");
    expect(challengeUrlInError("HTTP 500")).toBeNull();
  });
});

describe("clearances", () => {
  beforeEach(() => forgetClearance("https://site.example/"));

  it("adds the cookie and the browser's agent for the site and its subdomains only", () => {
    rememberClearance("https://site.example/page", session);
    expect(withClearance("https://api.site.example/v1", { "user-agent": "Mine/1.0", Cookie: "sid=1; cf_clearance=old", Referer: "r" })).toEqual({
      Referer: "r",
      Cookie: "sid=1; cf_clearance=abc",
      "User-Agent": "Browser/1.0",
    });
    expect(withClearance("https://other.example/", {})).toBeNull();
  });

  it("is dropped once the site challenges a request that carried it", async () => {
    installPlatform({
      http: { request: async (): Promise<HttpResponse> => ({ status: 403, url: "", headers: { "cf-mitigated": ["challenge"] }, body: CHALLENGE_HTML }) },
    } as unknown as Platform);
    clearNetFetchCache();
    rememberClearance("https://site.example/", session);
    await performNetFetch("https://site.example/list");
    expect(withClearance("https://site.example/", {})).toBeNull();
  });
});

describe("solveChallenge", () => {
  it("keeps the clearance the window earned, and shares one window per site", async () => {
    const requests: HttpRequest[] = [];
    const solve = vi.fn(async () => session);
    installPlatform({
      browser: { solveChallenge: solve },
      http: { request: async (request: HttpRequest): Promise<HttpResponse> => { requests.push(request); return { status: 200, url: "", headers: {}, body: "ok" }; } },
    } as unknown as Platform);
    clearNetFetchCache();
    const [a, b] = await Promise.all([solveChallenge("https://passed.example/a"), solveChallenge("https://passed.example/b")]);
    expect([a, b]).toEqual([true, true]);
    expect(solve).toHaveBeenCalledTimes(1);
    await performNetFetch("https://passed.example/data");
    expect(requests[0].headers).toMatchObject({ Cookie: "cf_clearance=abc", "User-Agent": "Browser/1.0" });
  });

  it("reports a window closed without passing", async () => {
    installPlatform({ browser: { solveChallenge: async () => null } } as unknown as Platform);
    expect(await solveChallenge("https://closed.example/")).toBe(false);
  });
});

describe("the error that names the check", () => {
  it("survives being wrapped on its way across IPC", () => {
    const error = cloudflareCheckError("demo", "https://site.example/list?page=2");
    const wrapped = new Error(`Error invoking remote method 'source:latest': ${error.message}`);
    expect(cloudflareCheckOf(wrapped)).toEqual({ url: "https://site.example/list?page=2", host: "site.example" });
    expect(cloudflareCheckOf(new Error("HTTP 500"))).toBeNull();
  });
});
