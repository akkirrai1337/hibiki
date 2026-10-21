import { describe, expect, it } from "vitest";
import type { PlayerLink } from "@shared/types";
import { menuLinks, renewStream, runPick, SelectionMachine, type PickDeps } from "./playerSelection";

const kodik: PlayerLink = { type: "EMBED", url: "https://kodik.info/serial/1", playerName: "Kodik", translation: "AniLibria" };
const alloha: PlayerLink = { type: "EMBED", url: "https://alloha.tv/1", playerName: "Alloha", translation: "AniLibria" };
const direct: PlayerLink = { type: "DIRECT_HLS", url: "https://cdn.example/1.m3u8", playerName: "Source", quality: "720p" };

function stream(of: PlayerLink, quality = "720p"): PlayerLink {
  return { type: "DIRECT_HLS", url: `${of.url}/${quality}.m3u8`, playerName: of.playerName, translation: of.translation, quality };
}

/** A promise the test settles by hand, for putting two picks in a chosen order. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

function deps(overrides: Partial<PickDeps> = {}): PickDeps {
  return {
    fetchList: async () => [kodik, alloha],
    resolve: async (embed) => [stream(embed)],
    isExpired: () => false,
    issued: () => {},
    withDataSaver: (_links, picked) => picked,
    preference: () => ({}),
    log: () => {},
    ...overrides,
  };
}

describe("player selection", () => {
  it("starts on the saved player", async () => {
    const machine = new SelectionMachine();
    await runPick(machine, deps({ preference: () => ({ playerName: "Alloha" }) }), "auto", null);
    expect(machine.getState().link).toEqual(stream(alloha));
  });

  it("keeps a player picked by hand when the slow saved one answers later", async () => {
    const machine = new SelectionMachine();
    const slowKodik = deferred<PlayerLink[]>();
    const d = deps({ preference: () => ({ playerName: "Kodik" }), resolve: (embed) => (embed === kodik ? slowKodik.promise : Promise.resolve([stream(embed)])) });
    const auto = runPick(machine, d, "auto", null);
    await Promise.resolve();
    await runPick(machine, d, "hand", alloha);
    slowKodik.resolve([stream(kodik)]);
    await auto;
    expect(machine.getState().link).toEqual(stream(alloha));
    expect(machine.getState().linkOwner).toBe("hand");
  });

  it("does not start an automatic pick over one made by hand", async () => {
    const machine = new SelectionMachine();
    const slowAlloha = deferred<PlayerLink[]>();
    const d = deps({ resolve: (embed) => (embed === alloha ? slowAlloha.promise : Promise.resolve([stream(embed)])) });
    const hand = runPick(machine, d, "hand", alloha);
    expect(await runPick(machine, d, "auto", null)).toBeNull();
    slowAlloha.resolve([stream(alloha)]);
    await hand;
    expect(machine.getState().link).toEqual(stream(alloha));
  });

  it("a hand pick resolves only its own player, never the list", async () => {
    const machine = new SelectionMachine();
    let listCalls = 0;
    await runPick(machine, deps({ fetchList: async () => { listCalls++; return [kodik, alloha]; } }), "hand", alloha);
    expect(listCalls).toBe(0);
    expect(machine.getState().link).toEqual(stream(alloha));
  });

  it("the later of two hand picks wins whatever order they finish in", async () => {
    const machine = new SelectionMachine();
    const slowKodik = deferred<PlayerLink[]>();
    const d = deps({ resolve: (embed) => (embed === kodik ? slowKodik.promise : Promise.resolve([stream(embed)])) });
    const first = runPick(machine, d, "hand", kodik);
    await runPick(machine, d, "hand", alloha);
    slowKodik.resolve([stream(kodik)]);
    expect(await first).toBeNull();
    expect(machine.getState().link).toEqual(stream(alloha));
  });

  it("an automatic pick moves on when a player's site is down", async () => {
    const machine = new SelectionMachine();
    const d = deps({ resolve: async (embed) => { if (embed === kodik) throw new Error("503"); return [stream(embed)]; } });
    await runPick(machine, d, "auto", null);
    expect(machine.getState().link).toEqual(stream(alloha));
    expect(machine.getState().failure).toBeNull();
  });

  it("a hand pick that fails says so, keeps what was playing, and can be retried", async () => {
    const machine = new SelectionMachine();
    let down = true;
    const d = deps({ resolve: async (embed) => { if (embed === kodik && down) throw new Error("503"); return [stream(embed)]; } });
    await runPick(machine, d, "hand", alloha);
    await runPick(machine, d, "hand", kodik);
    expect(machine.getState().link).toEqual(stream(alloha));
    expect(machine.getState().failure).toMatchObject({ owner: "hand", pick: kodik, reason: "failed" });
    down = false;
    await runPick(machine, d, "hand", machine.getState().failure!.pick);
    expect(machine.getState().link).toEqual(stream(kodik));
    expect(machine.getState().failure).toBeNull();
  });

  it("an empty list is unsupported, not a failure to retry", async () => {
    const machine = new SelectionMachine();
    await runPick(machine, deps({ fetchList: async () => [] }), "auto", null);
    expect(machine.getState().failure?.reason).toBe("unsupported");
  });

  it("an expired direct link is asked for again under a fresh URL", async () => {
    const machine = new SelectionMachine();
    const renewed = { ...direct, url: "https://cdn.example/1.m3u8?token=new" };
    const fresh: boolean[] = [];
    const d = deps({ fetchList: async (f) => { fresh.push(f); return [renewed]; }, isExpired: (link) => link.url === direct.url });
    await runPick(machine, d, "hand", direct);
    expect(fresh).toEqual([true]);
    expect(machine.getState().link).toEqual(renewed);
  });

  it("an expired stream from a player page is renewed from that page, never by asking for the list again", async () => {
    const machine = new SelectionMachine();
    let listCalls = 0;
    let resolves = 0;
    const d = deps({
      fetchList: async () => { listCalls++; return [kodik, alloha]; },
      resolve: async (embed) => { resolves++; return [stream(embed)]; },
    });
    await runPick(machine, d, "auto", null);
    expect(listCalls).toBe(1);
    await runPick(machine, d, "hand", alloha);
    // Back to Kodik's stream, its token gone by now.
    const expired = deps({ ...d, isExpired: (link) => link.url === stream(kodik).url });
    await runPick(machine, expired, "hand", stream(kodik));
    expect(listCalls).toBe(1);
    expect(resolves).toBe(3);
    expect(machine.getState().link).toEqual(stream(kodik));
  });

  it("a stream still in date is played again as it is", async () => {
    const machine = new SelectionMachine();
    let resolves = 0;
    const d = deps({ resolve: async (embed) => { resolves++; return [stream(embed)]; } });
    await runPick(machine, d, "hand", kodik);
    await runPick(machine, d, "hand", alloha);
    await runPick(machine, d, "hand", stream(kodik));
    expect(resolves).toBe(2);
    expect(machine.getState().link).toEqual(stream(kodik));
  });

  it("a playing stream is renewed from its player page without touching what plays", async () => {
    const machine = new SelectionMachine();
    let listCalls = 0;
    let n = 0;
    const d = deps({
      fetchList: async () => { listCalls++; return []; },
      resolve: async (embed) => [{ ...stream(embed), url: `${stream(embed).url}?t=${++n}` }],
    });
    await runPick(machine, d, "hand", kodik);
    const playing = machine.getState().link!;
    const renewed = await renewStream(machine, d, playing);
    expect(renewed?.url).toBe(`${stream(kodik).url}?t=2`);
    expect(machine.getState().link).toBe(playing);
    expect(listCalls).toBe(0);
  });

  it("an embed is never treated as expired: picking it resolves it directly", async () => {
    const machine = new SelectionMachine();
    let listCalls = 0;
    const d = deps({ fetchList: async () => { listCalls++; return []; }, isExpired: (link) => link.type !== "EMBED" });
    await runPick(machine, d, "hand", kodik);
    expect(listCalls).toBe(0);
    expect(machine.getState().link).toEqual(stream(kodik));
  });

  it("the menu shows a resolved embed as its renditions", async () => {
    const machine = new SelectionMachine();
    await runPick(machine, deps({ resolve: async (embed) => [stream(embed, "1080p"), stream(embed, "480p")] }), "hand", kodik);
    expect(menuLinks([kodik, alloha], machine.getState().expansions)).toEqual([stream(kodik, "1080p"), stream(kodik, "480p"), alloha]);
  });

  it("a fallback replaces an automatic pick", async () => {
    const machine = new SelectionMachine();
    await runPick(machine, deps(), "auto", null);
    await runPick(machine, deps(), "fallback", alloha);
    expect(machine.getState().linkOwner).toBe("fallback");
    expect(await runPick(machine, deps(), "auto", null)).toBeNull();
  });
});
