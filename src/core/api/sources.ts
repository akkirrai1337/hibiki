import type { HibikiApi } from "@shared/hibikiApi";
import type { AnimeTitle, PlaybackGroup } from "@shared/types";
import type { ExtensionRuntime } from "../extensions/runtime";
import { solveChallenge } from "../extensions/cloudflare";
import { logger } from "../logger";
import { recommendationsForSource } from "../recommendations/forSource";
import { cacheAnime, cachePlaybackGroups, getCachedAnime, getCachedAnimeMany, getCachedPlaybackGroups, getCachedPlaybackGroupsEntry } from "../offlineCache";

/** Everything `window.hibiki.sources` asks of an installed source: catalog, playback, account. */
export type SourcesApi = Omit<HibikiApi["sources"], "repositories" | "marketplace" | "install" | "uninstall" | "installedVersions" | "missingSources" | "onChanged">;

export function createSourcesApi(runtime: ExtensionRuntime): SourcesApi {
  return {
    list: async () => runtime.list(),
    search: (sourceId, request, requestId) => runtime.search(sourceId, request, requestId),
    cancelSearch: (requestId) => {
      runtime.cancelRequest(requestId);
    },
    latest: (sourceId, limit) => runtime.latest(sourceId, limit),

    // Falls back to whatever's cached (either from a previous successful fetch below, or from one of
    // this title's episodes finishing a download - see downloads.ts's cacheForOffline) - the source
    // itself being unreachable (offline, taken down, extension uninstalled, ...) shouldn't also take
    // down the title page, or a continue-watching/library card, for a title seen before. A title with
    // nothing cached still fails exactly as before.
    async getById(sourceId: string, id: string): Promise<AnimeTitle> {
      try {
        const anime = await runtime.getById(sourceId, id);
        await cacheAnime(sourceId, id, anime);
        return anime;
      } catch (err) {
        const cached = await getCachedAnime(sourceId, id);
        if (cached) return cached;
        throw err;
      }
    },

    // Cheap on purpose - one batched, indexed SQLite read for the whole screen, answered from disk
    // without waiting on any source.
    cachedTitles: (keys) => getCachedAnimeMany(keys),
    cachedPlaybackGroups: (sourceId, titleId) => getCachedPlaybackGroupsEntry(sourceId, titleId),

    async playbackGroups(sourceId: string, titleId: string): Promise<PlaybackGroup[]> {
      try {
        const groups = await runtime.getPlaybackGroups(sourceId, titleId);
        await cachePlaybackGroups(sourceId, titleId, groups);
        return groups;
      } catch (err) {
        const cached = await getCachedPlaybackGroups(sourceId, titleId);
        if (cached) return cached;
        throw err;
      }
    },
    playerLinks: (sourceId, titleId, groupId, episodeId, preference) => runtime.getPlayerLinks(sourceId, titleId, groupId, episodeId, preference),
    resolvePlayerLink: (link) => runtime.resolvePlayerLink(link),
    filterCatalog: (sourceId) => runtime.getFilterCatalog(sourceId),
    recommendations: (sourceId, sort) => recommendationsForSource(runtime, sourceId, sort),

    // Straight through to the source. The password is a parameter of this one call and is written
    // nowhere: whatever the source needs in order to prove itself again later, it puts in its own
    // store (see extensionStorage.ts), and the host never learns what that is.
    account: {
      get: (sourceId) => runtime.getAccount(sourceId),
      login: (sourceId, credentials) => runtime.login(sourceId, credentials),
      loginWeb: (sourceId) => runtime.loginWeb(sourceId),
      logout: (sourceId) => runtime.logout(sourceId),
    },
    comments: {
      list: (sourceId, request) => runtime.listComments(sourceId, request),
      post: (sourceId, request) => runtime.postComment(sourceId, request),
      vote: (sourceId, request) => runtime.voteComment(sourceId, request),
    },
    reviews: {
      list: (sourceId, request) => runtime.listReviews(sourceId, request),
      post: (sourceId, request) => runtime.postReview(sourceId, request),
    },
    settings: {
      read: (sourceId) => runtime.readSettings(sourceId),
      write: (sourceId, key, value) => runtime.writeSetting(sourceId, key, value),
    },
    listLibrary: (sourceId) => runtime.listLibrary(sourceId),

    // Silently skipped rather than refused when the switch is off: the player calls this on every
    // save and has no business knowing which sources report anything.
    async reportPlayback(sourceId, request) {
      if (!(await runtime.isActivitySyncEnabled(sourceId))) return false;
      const label = `${sourceId} video ${request.videoId} at ${request.positionSeconds}/${request.durationSeconds}s, ${request.watchedSeconds.length}s watched`;
      try {
        const accepted = await runtime.reportPlayback(sourceId, request);
        logger.info("account", `playback reported: ${label}${accepted ? "" : " (not accepted)"}`);
        return accepted;
      } catch (error) {
        logger.warn("account", `playback not reported: ${label}: ${error instanceof Error ? error.message : String(error)}`);
        throw error;
      }
    },
    async pingOnline(sourceId) {
      if (!(await runtime.isActivitySyncEnabled(sourceId))) return false;
      return runtime.pingOnline(sourceId);
    },
    syncLibraryEntry: (sourceId, request) => runtime.syncLibraryEntry(sourceId, request),
    solveChallenge: (url) => solveChallenge(url),
  };
}
