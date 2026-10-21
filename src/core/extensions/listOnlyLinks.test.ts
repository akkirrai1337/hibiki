import { describe, expect, it, vi } from "vitest";

vi.mock("./netFetch", () => ({ performNetFetch: vi.fn(), performNetFetchAll: vi.fn() }));
vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

import { ExtensionRuntime } from "./runtime";
import type { PlayerLink, PlayerLinkPreference } from "@shared/types";

type Internals = {
  resolvers: Map<string, { id: string; version: string; hosts: string[] }>;
  run: (method: string, sourceId: string, args: unknown[]) => Promise<PlayerLink[]>;
  snapshotFor: () => Promise<null>;
  attemptResolve: (resolver: { id: string }, link: PlayerLink) => Promise<PlayerLink[]>;
  getPlayerLinks: (sourceId: string, titleId: string, groupId: string, episodeId: string, preference?: PlayerLinkPreference) => Promise<PlayerLink[]>;
};

const embed = (host: string, name: string): PlayerLink => ({ url: `https://${host}/e/1`, type: "EMBED", playerName: name, translation: "Dub" });
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** A runtime whose source lists two players, and whose first player's page takes `resolveMs` to resolve. */
function runtimeWith(resolveMs: number) {
  const runtime = new ExtensionRuntime("unused") as unknown as Internals;
  runtime.resolvers.set("kodik", { id: "kodik", version: "1", hosts: ["kodik.test"] });
  runtime.resolvers.set("alloha", { id: "alloha", version: "1", hosts: ["alloha.test"] });
  const sourceReads = vi.fn(async () => {
    await wait(20);
    return [embed("kodik.test", "Kodik"), embed("alloha.test", "Alloha")];
  });
  runtime.run = sourceReads as unknown as Internals["run"];
  runtime.snapshotFor = async () => null;
  runtime.attemptResolve = async (resolver) => {
    await wait(resolveMs);
    return [{ url: `https://cdn/${resolver.id}.m3u8`, type: "DIRECT_HLS", playerName: resolver.id === "kodik" ? "Kodik" : "Alloha", translation: "Dub" }];
  };
  return { runtime, sourceReads };
}

describe("getPlayerLinks with listOnly", () => {
  it("answers with the source's own list while a stream is still being resolved", async () => {
    const { runtime } = runtimeWith(600);
    const preference = { playerName: "Kodik", translation: "Dub" };

    const started = Date.now();
    const full = runtime.getPlayerLinks("src", "t", "g", "e", preference);
    const list = await runtime.getPlayerLinks("src", "t", "g", "e", { ...preference, listOnly: true });
    const listTook = Date.now() - started;

    // Both players are there to pick from, unresolved - long before the resolving call is done.
    expect(list.map((link) => `${link.type}:${link.playerName}`)).toEqual(["EMBED:Kodik", "EMBED:Alloha"]);
    expect(listTook).toBeLessThan(400);
    const resolved = await full;
    expect(Date.now() - started).toBeGreaterThanOrEqual(600);
    expect(resolved[0].type).toBe("DIRECT_HLS");
  });

  it("reads the source once for the two calls made together", async () => {
    const { runtime, sourceReads } = runtimeWith(100);
    const preference = { playerName: "Kodik", translation: "Dub" };
    await Promise.all([
      runtime.getPlayerLinks("src", "t", "g", "e", preference),
      runtime.getPlayerLinks("src", "t", "g", "e", { ...preference, listOnly: true }),
    ]);
    expect(sourceReads).toHaveBeenCalledTimes(1);
  });

  it("leaves out embeds no resolver can open, like the full call does", async () => {
    const { runtime } = runtimeWith(10);
    runtime.run = (async () => [embed("kodik.test", "Kodik"), embed("unknown.test", "Mystery")]) as unknown as Internals["run"];
    const list = await runtime.getPlayerLinks("src", "t", "g", "e", { listOnly: true });
    expect(list.map((link) => link.playerName)).toEqual(["Kodik"]);
  });

  it("does not change what the full call returns", async () => {
    const { runtime } = runtimeWith(10);
    const full = await runtime.getPlayerLinks("src", "t", "g", "e", { playerName: "Kodik", translation: "Dub" });
    expect(full.map((link) => `${link.type}:${link.playerName}`)).toEqual(["DIRECT_HLS:Kodik", "EMBED:Alloha"]);
  });
});
