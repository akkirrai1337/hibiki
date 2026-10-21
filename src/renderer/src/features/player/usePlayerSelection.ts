import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { PlayerLink } from "@shared/types";
import { pickDataSaverLink, pickPlaybackFallback, type PlaybackPreference } from "@/lib/playerLinks";
import { autoQualityCap, describeConnection, isDataSaverActive } from "@/lib/connectionQuality";
import { hibiki } from "@/lib/hibiki";
import { log } from "@/lib/log";
import { menuLinks, renewStream, runPick, SelectionMachine, type PickDeps } from "./playerSelection";

// How long a stream URL is trusted before it is renewed instead of played as it is. Alloha's (vkvideo)
// tokens were seen dying six minutes after being issued, so the default stays well under that;
// Kodik's last hours, so a Kodik stream picked again within the hour plays without asking anew. One
// that dies sooner anyway is renewed mid-play (VideoPlayer) on the first refusal.
const DEFAULT_TOKEN_LIFETIME_MS = 3 * 60 * 1000;
const TOKEN_LIFETIME_MS: Record<string, number> = { kodik: 60 * 60 * 1000 };
function tokenLifetime(link: PlayerLink): number {
  return TOKEN_LIFETIME_MS[(link.playerName ?? "").trim().toLowerCase()] ?? DEFAULT_TOKEN_LIFETIME_MS;
}
// A stream refused this soon after being handed out is broken, not expired: renewing it would hand
// out the same thing, the fallback deals with it better.
const BROKEN_WITHIN_MS = 30 * 1000;
// One player's page into a stream. Longer than this and the pick says the site isn't answering
// (with Retry) instead of a spinner that never ends; the core gives up on its own at 90 s.
const RESOLVE_TIMEOUT_MS = 45_000;

// Where an automatic pick starts: at the height the connection's speed allows (lib/connectionQuality.ts).
// Read at the moment of picking, never subscribed to: the verdict changing mid-episode must not
// reload the stream that is playing. Picks of a named quality never come through here.
function withDataSaver(links: PlayerLink[], picked: PlayerLink): PlayerLink {
  const cap = autoQualityCap();
  if (cap === null) return picked;
  const lighter = pickDataSaverLink(links, picked, cap);
  if (lighter !== picked) log.info("player", `auto quality: starting at ${lighter.quality ?? "?"} instead of ${picked.quality ?? "?"}, up to ${cap}p (${describeConnection()})`);
  return lighter;
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`no answer in ${Math.round(ms / 1000)} s`)), ms);
    promise.then((value) => { clearTimeout(timer); resolve(value); }, (error) => { clearTimeout(timer); reject(error); });
  });
}

// What PlayerSwitchToast shows for one end of the switch.
function playerSwitchLabel(link: PlayerLink): string {
  return link.quality ? `${link.playerName ?? "?"} (${link.quality})` : (link.playerName ?? "?");
}

export interface PlayerSelectionOptions {
  sourceId: string;
  animeId: string;
  groupId: string;
  episodeId: string;
  /** The starting pick may be made: no downloaded copy plays instead, and the saved progress is read. */
  autoReady: boolean;
  /** The saved dub/player pick, read when the starting pick is made. */
  preference: () => PlaybackPreference;
}

/**
 * The watch page's player choice (see playerSelection.ts for the rules). The page reads `link` and
 * `links` and reports picks and failures; it decides nothing about what plays on its own.
 */
export function usePlayerSelection({ sourceId, animeId, groupId, episodeId, autoReady, preference }: PlayerSelectionOptions) {
  const queryClient = useQueryClient();
  // One per episode: whatever the previous episode still had in flight lands in its own, unseen.
  const machine = useMemo(() => new SelectionMachine(), [sourceId, animeId, groupId, episodeId]);
  const state = useSyncExternalStore(machine.subscribe, machine.getState);
  const preferenceRef = useRef(preference);
  preferenceRef.current = preference;

  // When each stream URL was handed out. Those URLs carry CDN tokens that expire, and the list is
  // kept for the whole episode, so a link that has been sitting here a while is not worth trying as it is.
  const issuedAtRef = useRef(new Map<string, number>());
  useEffect(() => { issuedAtRef.current.clear(); }, [machine]);

  // The source's plain list - no stream resolved inside it, so it answers at once however slow a
  // player is. `fresh`: the links handed out before have expired - the source is asked again, not the
  // episode list it saved for this title (core/extensions/titleSnapshots.ts).
  const listKey = useMemo(() => ["playerList", sourceId, animeId, groupId, episodeId], [sourceId, animeId, groupId, episodeId]);
  const fetchList = useCallback(async (fresh: boolean) => {
    const startedAt = performance.now();
    log.info("player", `link list asked for: source=${sourceId}, anime=${animeId}, group=${groupId}, episode=${episodeId}${fresh ? " (fresh)" : ""}`);
    try {
      const links = await hibiki.sources.playerLinks(sourceId, animeId, groupId, episodeId, { listOnly: true, ...(fresh ? { fresh: true } : {}) });
      log.info("player", `link list in ${Math.round(performance.now() - startedAt)}ms: ${links.map((item) => `${item.type}:${item.playerName ?? "?"}/${item.translation ?? "?"}/${item.quality ?? "?"}`).join(", ") || "none"}`);
      const issuedAt = Date.now();
      for (const item of links) issuedAtRef.current.set(item.url, issuedAt);
      return links;
    } catch (error) {
      log.error("player", `link list failed after ${Math.round(performance.now() - startedAt)}ms:`, error);
      throw error;
    }
  }, [sourceId, animeId, groupId, episodeId]);
  // staleTime: Infinity + no refetch on focus: a refetch hands out a new array, and nothing here
  // should move on its own mid-episode.
  const listQuery = useQuery({ queryKey: listKey, queryFn: () => fetchList(false), staleTime: Infinity, refetchOnWindowFocus: false });

  const isExpired = useCallback((link: PlayerLink) => {
    // An embed is a player page, not a stream: resolving it is what hands out fresh tokens.
    if (link.type === "EMBED") return false;
    const issuedAt = issuedAtRef.current.get(link.url);
    return issuedAt === undefined || Date.now() - issuedAt > tokenLifetime(link);
  }, []);

  const deps = useMemo((): PickDeps => ({
    fetchList: (fresh) => queryClient.fetchQuery({ queryKey: listKey, queryFn: () => fetchList(fresh), staleTime: fresh ? 0 : Infinity }),
    resolve: async (embed) => {
      const startedAt = performance.now();
      log.info("player", `resolving ${embed.playerName ?? "?"}/${embed.translation ?? "?"}`);
      const resolved = await withTimeout(hibiki.sources.resolvePlayerLink(embed, { dataSaver: isDataSaverActive() }), RESOLVE_TIMEOUT_MS);
      log.info("player", `${embed.playerName ?? "?"} resolved in ${Math.round(performance.now() - startedAt)}ms: ${resolved.map((item) => `${item.type}/${item.quality ?? "?"}`).join(", ") || "none"}`);
      return resolved;
    },
    isExpired,
    issued: (links) => {
      const issuedAt = Date.now();
      for (const item of links) issuedAtRef.current.set(item.url, issuedAt);
    },
    withDataSaver,
    preference: () => preferenceRef.current(),
    log: (message) => log.info("player", message),
  }), [queryClient, listKey, fetchList, isExpired]);

  // The starting pick, once per episode.
  const autoStartedRef = useRef<SelectionMachine | null>(null);
  useEffect(() => {
    if (!autoReady || autoStartedRef.current === machine) return;
    autoStartedRef.current = machine;
    void runPick(machine, deps, "auto", null);
  }, [autoReady, machine, deps]);

  const pickByHand = useCallback((link: PlayerLink) => { void runPick(machine, deps, "hand", link); }, [machine, deps]);

  const retry = useCallback(() => {
    const failure = machine.getState().failure;
    if (!failure) return;
    if (failure.owner === "auto" || !failure.pick) void runPick(machine, deps, "auto", null);
    else void runPick(machine, deps, failure.owner === "refresh" ? "refresh" : "hand", failure.pick);
  }, [machine, deps]);

  // A silent recovery looks, from the outside, like the episode changing player on its own - the
  // toast says it was deliberate.
  const [switchToast, setSwitchToast] = useState<{ fromLabel: string; toLabel: string } | null>(null);
  const onPlaybackFailure = useCallback((failed: PlayerLink, reason: string): boolean => {
    machine.failedUrls.add(failed.url);
    // A stream that played for a while and then failed has most likely just outlived its token: the
    // same stream under a fresh URL is the right recovery, not another provider.
    if (isExpired(failed)) {
      log.warn("player", `stream failed (${reason}) on an old link; asking for it again`);
      void runPick(machine, deps, "refresh", failed);
      return true;
    }
    const candidates = menuLinks(queryClient.getQueryData<PlayerLink[]>(listKey), machine.getState().expansions) ?? [];
    const fallback = pickPlaybackFallback(candidates, failed, machine.failedUrls);
    if (!fallback) return false;
    log.warn("player", `stream failed (${reason}); falling back ${failed.playerName ?? "?"}/${failed.quality ?? "?"} -> ${fallback.playerName ?? "?"}/${fallback.quality ?? "?"}`);
    setSwitchToast({ fromLabel: playerSwitchLabel(failed), toLabel: playerSwitchLabel(fallback) });
    void runPick(machine, deps, "fallback", fallback);
    return true;
  }, [machine, deps, isExpired, queryClient, listKey]);

  // The same stream under a freshly issued URL, for the player to move a playing stream onto without
  // restarting it (VideoPlayer's token renewal). Nothing here switches what plays.
  const refreshStream = useCallback(async (stale: PlayerLink): Promise<PlayerLink | null> => {
    const issuedAt = issuedAtRef.current.get(stale.url);
    if (issuedAt !== undefined && Date.now() - issuedAt < BROKEN_WITHIN_MS) return null;
    log.info("player", `renewing ${stale.playerName ?? "?"}/${stale.quality ?? "?"} in place`);
    return renewStream(machine, deps, stale);
  }, [machine, deps]);

  return {
    link: state.link ?? undefined,
    linkOwner: state.linkOwner,
    /** The menu's list: the source's, with every player resolved so far shown as its qualities. */
    links: useMemo(() => menuLinks(listQuery.data, state.expansions), [listQuery.data, state.expansions]),
    listQuery,
    /** A pick is being worked on. */
    switching: state.pending !== null,
    failure: state.failure,
    dismissFailure: useCallback(() => machine.dismissFailure(), [machine]),
    pickByHand,
    retry,
    onPlaybackFailure,
    refreshStream,
    switchToast,
    clearSwitchToast: useCallback(() => setSwitchToast(null), []),
  };
}
