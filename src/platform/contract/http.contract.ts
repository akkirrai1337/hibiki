// What every HttpPort must do, whatever it is built on. Extension scripts were written against
// this behaviour (Node's fetch on desktop, OkHttp on Android), so a transport that differs here
// makes a source work on one platform and not the other.
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { HttpPort } from "../types";
import { startTestServer } from "./testServer";

export function describeHttpContract(name: string, http: HttpPort): void {
  describe(`HttpPort contract: ${name}`, () => {
    let server: Awaited<ReturnType<typeof startTestServer>>;
    beforeAll(async () => {
      server = await startTestServer();
    });
    afterAll(() => server.close());

    it("sends browser-forbidden headers exactly as given", async () => {
      const response = await http.request({
        url: `${server.url}/echo`,
        headers: { Referer: "https://example.org/page", Cookie: "session=abc", "User-Agent": "TestAgent/1.0" },
      });
      const echoed = JSON.parse(response.body) as { headers: Record<string, string> };
      expect(response.status).toBe(200);
      expect(echoed.headers.referer).toBe("https://example.org/page");
      expect(echoed.headers.cookie).toBe("session=abc");
      expect(echoed.headers["user-agent"]).toBe("TestAgent/1.0");
    });

    it("sends a POST body with its method", async () => {
      const response = await http.request({
        url: `${server.url}/echo`,
        method: "post",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "a=1&b=2",
      });
      const echoed = JSON.parse(response.body) as { method: string; body: string; headers: Record<string, string> };
      expect(echoed.method).toBe("POST");
      expect(echoed.body).toBe("a=1&b=2");
      expect(echoed.headers["content-type"]).toBe("application/x-www-form-urlencoded");
    });

    it("keeps every Set-Cookie as its own entry", async () => {
      const response = await http.request({ url: `${server.url}/cookies` });
      expect(response.headers["set-cookie"]).toEqual(["a=1; Path=/", "b=2; Path=/; HttpOnly"]);
    });

    it("keeps no cookie jar between requests", async () => {
      await http.request({ url: `${server.url}/cookies` });
      const response = await http.request({ url: `${server.url}/echo` });
      const echoed = JSON.parse(response.body) as { headers: Record<string, string> };
      expect(echoed.headers.cookie).toBeUndefined();
    });

    it("follows redirects by default and reports where it landed", async () => {
      const response = await http.request({ url: `${server.url}/redirect` });
      expect(response.status).toBe(200);
      expect(response.url).toBe(`${server.url}/echo`);
    });

    it("answers with the redirect itself in manual mode", async () => {
      const response = await http.request({ url: `${server.url}/redirect`, redirect: "manual" });
      expect(response.status).toBe(302);
      expect(response.headers.location).toEqual(["/echo"]);
    });

    it("decompresses gzip bodies", async () => {
      const response = await http.request({ url: `${server.url}/gzip` });
      expect(response.body).toBe("compressed body");
    });

    it("resolves error statuses instead of throwing", async () => {
      const response = await http.request({ url: `${server.url}/status?code=503` });
      expect(response.status).toBe(503);
      expect(response.body).toBe("status body");
    });

    it("rejects when the timeout passes without a response", async () => {
      await expect(http.request({ url: `${server.url}/slow`, timeoutMs: 200 })).rejects.toThrow();
    });

    it("rejects when aborted", async () => {
      const controller = new AbortController();
      const pending = http.request({ url: `${server.url}/slow`, signal: controller.signal });
      controller.abort();
      await expect(pending).rejects.toThrow();
    });
  });
}
