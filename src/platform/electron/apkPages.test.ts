import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApkHostProcess } from "./apkHost";
import { interfaceScript, PageBridge, parseBridgeCall, type PageDriver, type PageEvents } from "./apkPages";

class FakeHost {
  started: Array<() => void> = [];
  stopped: Array<() => void> = [];
  sent: Array<{ route: string; body: Record<string, unknown> }> = [];
  stream: ReadableStreamDefaultController<Uint8Array> | null = null;
  fetches = 0;

  onStarted(listener: () => void) { this.started.push(listener); }
  onStopped(listener: () => void) { this.stopped.push(listener); }
  async request(_method: string, route: string, body: Record<string, unknown>) {
    this.sent.push({ route, body });
    return {};
  }
  async fetchRaw(_path: string, init: { signal?: AbortSignal } = {}) {
    this.fetches++;
    const body = new ReadableStream<Uint8Array>({
      start: (controller) => {
        this.stream = controller;
        init.signal?.addEventListener("abort", () => { try { controller.close(); } catch { /* already closed */ } });
      },
    });
    return new Response(body, { status: 200 });
  }
  push(text: string) { this.stream!.enqueue(new TextEncoder().encode(text)); }
}

class FakeDriver implements PageDriver {
  calls: unknown[][] = [];
  closed = false;
  result: string | null = '"title"';
  constructor(readonly events: PageEvents) {}
  load(url: string, options: unknown) { this.calls.push(["load", url, options]); }
  loadData(baseUrl: string, data: string, mimeType: string, userAgent: string) { this.calls.push(["loadData", baseUrl, data, mimeType, userAgent]); }
  async evaluate(script: string) { this.calls.push(["evaluate", script]); return this.result; }
  stop() { this.calls.push(["stop"]); }
  addInterface(name: string, methods: string[]) { this.calls.push(["interface", name, methods]); }
  close() { this.closed = true; }
}

describe("PageBridge", () => {
  let host: FakeHost;
  let drivers: FakeDriver[];
  let bridge: PageBridge;

  beforeEach(() => {
    host = new FakeHost();
    drivers = [];
    bridge = new PageBridge(host as unknown as ApkHostProcess, (events) => {
      const driver = new FakeDriver(events);
      drivers.push(driver);
      return driver;
    });
  });
  afterEach(() => vi.useRealTimers());

  const line = (command: object) => JSON.stringify(command);
  const flush = () => new Promise((resolve) => setTimeout(resolve, 5));

  it("opens a page on its first command and loads what it is told to", () => {
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/p", headers: { Referer: "https://a.example/" }, userAgent: "UA", cookies: "a=b" }));
    expect(drivers).toHaveLength(1);
    expect(drivers[0].calls).toEqual([["load", "https://embed.example/p", { headers: { Referer: "https://a.example/" }, userAgent: "UA", cookies: "a=b" }]]);
    // The same page id is the same page.
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/q" }));
    expect(drivers).toHaveLength(1);
    bridge.dispatch(line({ cmd: "load", page: 2, url: "https://other.example/" }));
    expect(drivers).toHaveLength(2);
    expect(bridge.openPages).toBe(2);
  });

  it("reports what a page does, tagged with its id", () => {
    bridge.dispatch(line({ cmd: "load", page: 7, url: "https://embed.example/" }));
    drivers[0].events.request({ url: "https://cdn.example/a.m3u8", method: "GET", headers: { Referer: "x" }, mainFrame: false });
    drivers[0].events.finished("https://embed.example/", ["t=1; Path=/"]);
    drivers[0].events.failed("https://embed.example/", -105, "ERR_NAME_NOT_RESOLVED");
    expect(host.sent).toEqual([
      { route: "/pages/event", body: { page: 7, type: "request", url: "https://cdn.example/a.m3u8", method: "GET", headers: { Referer: "x" }, mainFrame: false } },
      { route: "/pages/event", body: { page: 7, type: "finished", url: "https://embed.example/", setCookies: ["t=1; Path=/"] } },
      { route: "/pages/event", body: { page: 7, type: "error", url: "https://embed.example/", code: -105, description: "ERR_NAME_NOT_RESOLVED" } },
    ]);
  });

  it("runs a script and sends its result back with the call it answers", async () => {
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/" }));
    bridge.dispatch(line({ cmd: "evaluate", page: 1, call: 4, script: "document.title" }));
    await flush();
    expect(drivers[0].calls).toContainEqual(["evaluate", "document.title"]);
    expect(host.sent.at(-1)).toEqual({ route: "/pages/event", body: { page: 1, type: "evaluated", call: 4, result: '"title"' } });
  });

  it("answers a script that threw with nothing, not with silence", async () => {
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/" }));
    drivers[0].evaluate = async () => { throw new Error("boom"); };
    bridge.dispatch(line({ cmd: "evaluate", page: 1, call: 9, script: "x" }));
    await flush();
    expect(host.sent.at(-1)?.body).toMatchObject({ type: "evaluated", call: 9, result: null });
  });

  it("stops and closes pages, and tolerates commands for pages that are gone", () => {
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/" }));
    bridge.dispatch(line({ cmd: "stop", page: 1 }));
    expect(drivers[0].calls).toContainEqual(["stop"]);
    bridge.dispatch(line({ cmd: "close", page: 1 }));
    expect(drivers[0].closed).toBe(true);
    expect(bridge.openPages).toBe(0);
    bridge.dispatch(line({ cmd: "close", page: 1 }));
    bridge.dispatch(line({ cmd: "stop", page: 99 }));
    bridge.dispatch("not json at all");
    expect(drivers).toHaveLength(1);
  });

  it("does not let a failing page take the bridge down", () => {
    const failing = new PageBridge(host as unknown as ApkHostProcess, () => { throw new Error("no window"); });
    expect(() => failing.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/" }))).not.toThrow();
    expect(failing.openPages).toBe(0);
  });

  it("closes a page nobody closed", () => {
    vi.useFakeTimers();
    bridge.dispatch(line({ cmd: "load", page: 1, url: "https://embed.example/" }));
    vi.advanceTimersByTime(2 * 60_000);
    expect(drivers[0].closed).toBe(false);
    vi.advanceTimersByTime(2 * 60_000);
    expect(drivers[0].closed).toBe(true);
    expect(bridge.openPages).toBe(0);
  });

  it("reads commands off the stream even when a line arrives in pieces", async () => {
    host.started[0]();
    await flush();
    expect(host.fetches).toBe(1);
    const first = line({ cmd: "load", page: 1, url: "https://embed.example/one" });
    host.push("\n\n" + first.slice(0, 20));
    await flush();
    expect(drivers).toHaveLength(0);
    host.push(first.slice(20) + "\n" + line({ cmd: "load", page: 2, url: "https://embed.example/two" }) + "\n");
    await flush();
    expect(drivers).toHaveLength(2);
    expect((drivers[1].calls[0] as unknown[])[1]).toBe("https://embed.example/two");
  });

  it("closes every page when the host goes away, and listens again when it comes back", async () => {
    host.started[0]();
    await flush();
    host.push(line({ cmd: "load", page: 1, url: "https://embed.example/" }) + "\n");
    await flush();
    expect(bridge.openPages).toBe(1);
    host.stopped[0]();
    expect(drivers[0].closed).toBe(true);
    expect(bridge.openPages).toBe(0);
    host.started[0]();
    await flush();
    expect(host.fetches).toBe(2);
  });

  it("adds an extension's javascript interface to its page, and sends the page's calls back", () => {
    bridge.dispatch(line({ cmd: "interface", page: 3, name: "SenshiBridge", methods: ["onResult", "onError"] }));
    expect(drivers[0].calls).toEqual([["interface", "SenshiBridge", ["onResult", "onError"]]]);
    drivers[0].events.jsCall("SenshiBridge", "onResult", ['{"url":"x"}', null]);
    expect(host.sent).toEqual([{ route: "/pages/event", body: { page: 3, type: "jsCall", name: "SenshiBridge", method: "onResult", args: ['{"url":"x"}', null] } }]);
  });
});

describe("javascript interface script", () => {
  it("puts the object on the page, and its calls reach the bridge as JSON", () => {
    const calls: string[] = [];
    const page: Record<string, unknown> = { __hibikiJavaBridge: (payload: string) => calls.push(payload) };
    new Function("window", interfaceScript("HexaCap", ["onToken", "onError"]))(page);
    const hexa = page.HexaCap as { onToken: (...args: unknown[]) => void };
    hexa.onToken("tok", 5, undefined, { a: 1 });
    expect(calls.map((payload) => parseBridgeCall(payload))).toEqual([{ name: "HexaCap", method: "onToken", args: ["tok", 5, null, "[object Object]"] }]);
  });

  it("does nothing on a page without the bridge", () => {
    const page: Record<string, unknown> = {};
    new Function("window", interfaceScript("X", ["a"]))(page);
    expect(page.X).toBeUndefined();
  });

  it("ignores payloads that are not bridge calls", () => {
    expect(parseBridgeCall("not json")).toBeNull();
    expect(parseBridgeCall(JSON.stringify({ name: "X" }))).toBeNull();
  });
});
