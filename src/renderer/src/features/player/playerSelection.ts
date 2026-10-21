import type { PlayerLink } from "@shared/types";
import { pickDefaultLink, pickPlaybackFallback, pickPreferredLink, pickRefreshedLink, pickResolvedLink, type PlaybackPreference } from "@/lib/playerLinks";

/**
 * What plays on the watch page, and who decided it - in one place.
 *
 * The page used to keep this in a dozen flags and refs (the picked link, "the saved pick is still
 * being applied", "a resolve is running", "a refresh is running", the request counter, ...) set
 * from several effects that each ran when their own data arrived; every new case added a flag, and
 * every pair of flags was a race (a saved pick arriving late replaced the player picked by hand).
 * Here one request is current at a time, a newer one makes every older one's result void, and an
 * automatic pick never replaces a deliberate one.
 *
 * The page's link list is only ever the source's plain list (fast); a stream is resolved for the one
 * link being picked, never for the whole list - so a slow player holds up only itself.
 */

/**
 * Who started a pick. "auto" - the episode's starting pick (the saved one, else the default);
 * "hand" - the player menu; "fallback" - a stream that failed, moved to another route; "refresh" -
 * a stream whose token expired, asked for again.
 */
export type PickOwner = "auto" | "hand" | "fallback" | "refresh";

/** "failed" - the page could not be turned into a stream (its site is down, blocked or changed), a
 * retry may help; "unsupported" - nothing in the list is something the player can play. */
export type PickFailureReason = "failed" | "unsupported";

export interface PickFailure {
  owner: PickOwner;
  pick: PlayerLink | null;
  reason: PickFailureReason;
}

export interface SelectionState {
  /** What plays. Stays while a newer pick resolves, so the old stream keeps going until replaced. */
  link: PlayerLink | null;
  linkOwner: PickOwner | null;
  /** The pick being worked on; `pick` is null while an automatic one is still choosing. */
  pending: { request: number; owner: PickOwner; pick: PlayerLink | null } | null;
  failure: PickFailure | null;
  /** Resolved renditions by the embed they came from: the menu shows them in its place. */
  expansions: ReadonlyMap<string, PlayerLink[]>;
}

export class PickFailedError extends Error {
  constructor(readonly reason: PickFailureReason, message: string) {
    super(message);
  }
}

/** One episode's selection. A new episode gets a new one: results of the old one's requests land nowhere. */
export class SelectionMachine {
  private state: SelectionState = { link: null, linkOwner: null, pending: null, failure: null, expansions: new Map() };
  private request = 0;
  private listeners = new Set<() => void>();
  /** Stream URLs that failed to play this episode: never a fallback again. */
  readonly failedUrls = new Set<string>();

  getState = (): SelectionState => this.state;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /**
   * Starts a pick and returns its request number, or null when it is refused: an automatic pick
   * never replaces one somebody (or a failure recovery) already made.
   */
  start(owner: PickOwner, pick: PlayerLink | null): number | null {
    if (owner === "auto") {
      const decided = this.state.pending?.owner ?? this.state.linkOwner;
      if (decided && decided !== "auto") return null;
    }
    this.request += 1;
    this.set({ pending: { request: this.request, owner, pick }, failure: null });
    return this.request;
  }

  isCurrent(request: number): boolean {
    return this.state.pending?.request === request;
  }

  /** The pick's stream. False when a newer pick has superseded it meanwhile (nothing changes). */
  settle(request: number, link: PlayerLink): boolean {
    const pending = this.state.pending;
    if (pending?.request !== request) return false;
    this.set({ link, linkOwner: pending.owner, pending: null, failure: null });
    return true;
  }

  fail(request: number, reason: PickFailureReason): boolean {
    const pending = this.state.pending;
    if (pending?.request !== request) return false;
    this.set({ pending: null, failure: { owner: pending.owner, pick: pending.pick, reason } });
    return true;
  }

  dismissFailure(): void {
    if (this.state.failure) this.set({ failure: null });
  }

  expand(embed: PlayerLink, renditions: PlayerLink[]): void {
    const expansions = new Map(this.state.expansions);
    expansions.set(embed.url, renditions);
    this.set({ expansions });
  }

  private set(patch: Partial<SelectionState>): void {
    this.state = { ...this.state, ...patch };
    for (const listener of this.listeners) listener();
  }
}

/** The menu's list: the source's, with each embed resolved so far replaced by its renditions. */
export function menuLinks(list: PlayerLink[] | undefined, expansions: ReadonlyMap<string, PlayerLink[]>): PlayerLink[] | undefined {
  if (!list || expansions.size === 0) return list;
  return list.flatMap((link) => (link.type === "EMBED" && expansions.get(link.url)) || [link]);
}

/** What the pipeline needs from the outside world - injected, so it runs the same in tests. */
export interface PickDeps {
  /** The source's plain list; `fresh` asks the source again (the old URLs expired). */
  fetchList(fresh: boolean): Promise<PlayerLink[]>;
  /** One embed into its streams. */
  resolve(embed: PlayerLink): Promise<PlayerLink[]>;
  /** Whether a direct stream URL has probably outlived its token. Embeds never expire. */
  isExpired(link: PlayerLink): boolean;
  /** Marks stream URLs as just issued. */
  issued(links: PlayerLink[]): void;
  /** The lighter rendition an automatic pick starts on, when the connection asks for one. */
  withDataSaver(links: PlayerLink[], picked: PlayerLink): PlayerLink;
  /** The saved dub/player pick, read when the automatic pick starts. */
  preference(): PlaybackPreference;
  log(message: string): void;
}

/** How many embeds an automatic pick tries before saying the episode cannot be played. */
export const AUTO_MAX_ATTEMPTS = 5;

/** An embed's streams, with the pick that led there kept through the expansion. */
async function resolveEmbed(machine: SelectionMachine, deps: PickDeps, embed: PlayerLink, requested: PlayerLink): Promise<PlayerLink> {
  let resolved: PlayerLink[];
  try {
    resolved = await deps.resolve(embed);
  } catch (error) {
    throw new PickFailedError("failed", `${embed.playerName ?? "?"}: ${error instanceof Error ? error.message : String(error)}`);
  }
  const playable = resolved.filter((link) => link.type !== "EMBED");
  if (playable.length === 0) throw new PickFailedError("failed", `${embed.playerName ?? "?"} gave no playable stream`);
  deps.issued(playable);
  machine.expand(embed, playable);
  const pick = pickResolvedLink(playable, requested)!;
  // A quality asked for by name stays; a player picked without one starts light when saving.
  return requested.quality ? pick : deps.withDataSaver(playable, pick);
}

/** The same stream under freshly issued URLs (its old ones expired), or another route of the same dub. */
async function refreshed(machine: SelectionMachine, deps: PickDeps, stale: PlayerLink): Promise<PlayerLink> {
  const fresh = await deps.fetchList(true);
  const next = pickRefreshedLink(fresh, stale) ?? pickPlaybackFallback(fresh, stale, machine.failedUrls);
  if (!next) throw new PickFailedError("failed", `nothing left to play after asking for ${stale.playerName ?? "?"} again`);
  return next.type === "EMBED" ? resolveEmbed(machine, deps, next, stale) : next;
}

/** A given link into something playable. */
async function obtain(machine: SelectionMachine, deps: PickDeps, owner: PickOwner, pick: PlayerLink): Promise<PlayerLink> {
  if (owner === "refresh" || (pick.type !== "EMBED" && deps.isExpired(pick))) return refreshed(machine, deps, pick);
  return pick.type === "EMBED" ? resolveEmbed(machine, deps, pick, pick) : pick;
}

/** The episode's starting pick: the saved one, else the default; an embed that fails moves on to the next route. */
async function obtainAuto(machine: SelectionMachine, deps: PickDeps, request: number): Promise<PlayerLink> {
  const list = await deps.fetchList(false);
  if (list.length === 0) throw new PickFailedError("unsupported", "the source lists no links");
  const preferred = pickPreferredLink(list, deps.preference());
  let candidate: PlayerLink | undefined = preferred ?? pickDefaultLink(list) ?? list[0];
  const tried = new Set<string>();
  let lastError: unknown = null;
  for (let attempt = 0; candidate && attempt < AUTO_MAX_ATTEMPTS; attempt++) {
    if (!machine.isCurrent(request)) throw new PickFailedError("failed", "superseded");
    if (candidate.type !== "EMBED") return deps.withDataSaver(list, candidate);
    try {
      return await resolveEmbed(machine, deps, candidate, candidate);
    } catch (error) {
      lastError = error;
      deps.log(`auto pick: ${error instanceof Error ? error.message : String(error)}; trying another player`);
      tried.add(candidate.url);
      candidate = pickPlaybackFallback(list, candidate, new Set([...tried, ...machine.failedUrls]));
    }
  }
  throw lastError instanceof PickFailedError ? lastError : new PickFailedError("failed", "no player gave a stream");
}

/**
 * Runs one pick to its end. Whatever the outcome, it lands only if no newer pick has started since;
 * returns the stream it settled on (null when refused, superseded or failed).
 */
export async function runPick(machine: SelectionMachine, deps: PickDeps, owner: PickOwner, pick: PlayerLink | null): Promise<PlayerLink | null> {
  const request = machine.start(owner, pick);
  if (request === null) {
    deps.log(`${owner} pick skipped: a deliberate pick is already in place`);
    return null;
  }
  try {
    const link = owner === "auto" || !pick ? await obtainAuto(machine, deps, request) : await obtain(machine, deps, owner, pick);
    if (!machine.settle(request, link)) {
      deps.log(`discarding superseded ${owner} pick ${request}`);
      return null;
    }
    return link;
  } catch (error) {
    const reason = error instanceof PickFailedError ? error.reason : "failed";
    if (machine.fail(request, reason)) deps.log(`${owner} pick failed: ${error instanceof Error ? error.message : String(error)}`);
    return null;
  }
}
