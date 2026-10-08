// The `window.hibiki.tracking` part both hosts share. Only AniList exists today; the tracker id is in
// every call so MyAnimeList can join without the renderer changing shape.
import type { HibikiApi } from "@shared/hibikiApi";
import { IPC } from "@shared/ipc";
import type { TrackerId } from "@shared/types";
import { getPlatform } from "../platform";
import * as anilist from "../tracking/tracker";

export type TrackingApi = Omit<HibikiApi["tracking"], "onChanged" | "onImportProgress">;

function only(tracker: TrackerId): void {
  if (tracker !== "anilist") throw new Error(`Unknown tracker: ${String(tracker)}`);
}

export function createTrackingApi(runtime: anilist.ImportRuntime): TrackingApi {
  return {
    account: async (tracker) => (only(tracker), anilist.getAccount()),
    signIn: async (tracker) => (only(tracker), anilist.beginSignIn()),
    signOut: async (tracker) => (only(tracker), anilist.signOut()),
    getLink: async (tracker, sourceId, animeId) => (only(tracker), anilist.getLink(sourceId, animeId)),
    search: async (tracker, query) => (only(tracker), anilist.search(query)),
    setLink: async (tracker, sourceId, animeId, mediaId) => (only(tracker), anilist.setLink(sourceId, animeId, mediaId)),
    removeFromList: async (tracker, sourceId, animeId) => (only(tracker), anilist.removeFromList(sourceId, animeId)),
    setFavourite: async (tracker, sourceId, animeId, favourite) => (only(tracker), anilist.setFavourite(sourceId, animeId, favourite)),
    importLibrary: async (tracker, sourceId) => {
      only(tracker);
      return anilist.importLibrary(runtime, sourceId, (done, total) => getPlatform().events.emit(IPC.trackingImportProgress, { done, total }));
    },
  };
}

/** For the hosts' deep-link handlers: true when `url` was AniList's sign-in redirect (and is handled). */
export function handleTrackingRedirect(url: string): boolean {
  if (!anilist.isAniListRedirect(url)) return false;
  void anilist.completeSignIn(url);
  return true;
}
