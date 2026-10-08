import { describe, expect, it, vi } from "vitest";

// The race logic under test needs no network and no log output.
vi.mock("./netFetch", () => ({ performNetFetch: vi.fn(), performNetFetchAll: vi.fn() }));
vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

import { ExtensionRuntime } from "./runtime";
import type { PlayerLink } from "@shared/types";

type Internals = {
  resolvers: Map<string, { id: string; version: string; hosts: string[]; runtime?: "NODE" | "BROWSER" }>;
  attemptResolve: (resolver: { id: string }, link: PlayerLink) => Promise<PlayerLink[]>;
  resolveEmbedLinks: (links: PlayerLink[]) => Promise<PlayerLink[]>;
};

const embed = (host: string): PlayerLink => ({ url: `https://${host}/e/1`, type: "EMBED", playerName: host });
const stream = (id: string): PlayerLink => ({ url: `https://cdn/${id}.m3u8`, type: "DIRECT_HLS" });
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function runtimeWith(resolvers: Array<{ id: string; runtime?: "NODE" | "BROWSER" }>, attempt: Internals["attemptResolve"]) {
  const runtime = new ExtensionRuntime("unused") as unknown as Internals;
  for (const r of resolvers) runtime.resolvers.set(r.id, { id: r.id, version: "1", hosts: [`${r.id}.test`], runtime: r.runtime });
  runtime.attemptResolve = attempt;
  return runtime;
}

describe("resolveEmbedLinks", () => {
  it("does not wait for a failing resolver before trying the next one", async () => {
    const runtime = runtimeWith([{ id: "slow" }, { id: "fast" }], async (resolver) => {
      if (resolver.id === "slow") {
        await wait(400);
        throw new Error("captured streams not reachable");
      }
      return [stream("fast")];
    });

    const started = Date.now();
    const result = await runtime.resolveEmbedLinks([embed("slow.test"), embed("fast.test")]);

    expect(result[0].url).toBe("https://cdn/fast.m3u8");
    // The second attempt starts after the stagger (1.2s) at the latest, or right away on a failure -
    // never after the slow one has run to completion *and* been given its retry.
    expect(Date.now() - started).toBeLessThan(1_600);
    // The link that was resolved is replaced by its streams; the other one is kept as a fallback.
    expect(result.map((l) => l.type)).toEqual(["DIRECT_HLS", "EMBED"]);
  });

  it("lets the first-listed resolver win when it answers promptly", async () => {
    const calls: string[] = [];
    const runtime = runtimeWith([{ id: "a" }, { id: "b" }], async (resolver) => {
      calls.push(resolver.id);
      await wait(resolver.id === "a" ? 50 : 10);
      return [stream(resolver.id)];
    });

    const result = await runtime.resolveEmbedLinks([embed("a.test"), embed("b.test")]);

    expect(result[0].url).toBe("https://cdn/a.m3u8");
    expect(calls).toEqual(["a"]); // b was never started: a answered inside the stagger window
  });

  it("falls back to the untouched link list when every resolver fails", async () => {
    const runtime = runtimeWith([{ id: "a" }, { id: "b" }], async () => {
      throw new Error("nope");
    });
    const links = [embed("a.test"), embed("b.test")];

    expect(await runtime.resolveEmbedLinks(links)).toEqual(links);
  });

  it("never runs two browser-runtime resolvers at once", async () => {
    let running = 0;
    let peak = 0;
    const runtime = runtimeWith([{ id: "x", runtime: "BROWSER" }, { id: "y", runtime: "BROWSER" }], async () => {
      running += 1;
      peak = Math.max(peak, running);
      await wait(60);
      running -= 1;
      throw new Error("no stream");
    });

    await runtime.resolveEmbedLinks([embed("x.test"), embed("y.test")]);

    expect(peak).toBe(1);
  });
});
