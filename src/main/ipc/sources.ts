import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import type { PlayerLink, PlayerLinkPreference, SearchRequest } from "@shared/types";
import { createSourcesApi, type SourcesApi } from "../../core/api/sources";
import type { ExtensionRuntime } from "../../core/extensions/runtime";

export function registerSourceHandlers(runtime: ExtensionRuntime): void {
  const sources = createSourcesApi(runtime);

  ipcMain.handle(IPC.sourcesList, () => sources.list());
  ipcMain.handle(IPC.sourceSearch, (_e, sourceId: string, request: SearchRequest, requestId?: string) => sources.search(sourceId, request, requestId));
  ipcMain.on(IPC.sourceSearchCancel, (_e, requestId: string) => sources.cancelSearch(requestId));
  ipcMain.handle(IPC.sourceLatest, (_e, sourceId: string, limit: number) => sources.latest(sourceId, limit));
  ipcMain.handle(IPC.sourceGetById, (_e, sourceId: string, id: string) => sources.getById(sourceId, id));
  ipcMain.handle(IPC.sourceCachedTitles, (_e, keys: Array<{ sourceId: string; animeId: string }>) => sources.cachedTitles(keys));
  ipcMain.handle(IPC.sourceCachedPlaybackGroups, (_e, sourceId: string, titleId: string) => sources.cachedPlaybackGroups(sourceId, titleId));
  ipcMain.handle(IPC.sourcePlaybackGroups, (_e, sourceId: string, titleId: string) => sources.playbackGroups(sourceId, titleId));
  ipcMain.handle(
    IPC.sourcePlayerLinks,
    (_e, sourceId: string, titleId: string, groupId: string, episodeId: string, preference?: PlayerLinkPreference) =>
      sources.playerLinks(sourceId, titleId, groupId, episodeId, preference),
  );
  ipcMain.handle(IPC.sourceResolvePlayerLink, (_e, link: PlayerLink) => sources.resolvePlayerLink(link));
  ipcMain.handle(IPC.sourceFilterCatalog, (_e, sourceId: string) => sources.filterCatalog(sourceId));
  ipcMain.handle(IPC.sourceRecommendations, (_e, sourceId: string, sort?: string) => sources.recommendations(sourceId, sort));

  ipcMain.handle(IPC.sourceLogin, (_e, sourceId: string, credentials: { login: string; password: string }) => sources.account.login(sourceId, credentials));
  ipcMain.handle(IPC.sourceLoginWeb, (_e, sourceId: string) => sources.account.loginWeb(sourceId));
  ipcMain.handle(IPC.sourceLogout, (_e, sourceId: string) => sources.account.logout(sourceId));
  ipcMain.handle(IPC.sourceAccount, (_e, sourceId: string) => sources.account.get(sourceId));
  ipcMain.handle(IPC.sourceComments, (_e, sourceId: string, request: Parameters<SourcesApi["comments"]["list"]>[1]) => sources.comments.list(sourceId, request));
  ipcMain.handle(IPC.sourcePostComment, (_e, sourceId: string, request: Parameters<SourcesApi["comments"]["post"]>[1]) => sources.comments.post(sourceId, request));
  ipcMain.handle(IPC.sourceVoteComment, (_e, sourceId: string, request: Parameters<SourcesApi["comments"]["vote"]>[1]) => sources.comments.vote(sourceId, request));
  ipcMain.handle(IPC.sourceReviews, (_e, sourceId: string, request: Parameters<SourcesApi["reviews"]["list"]>[1]) => sources.reviews.list(sourceId, request));
  ipcMain.handle(IPC.sourcePostReview, (_e, sourceId: string, request: Parameters<SourcesApi["reviews"]["post"]>[1]) => sources.reviews.post(sourceId, request));
  ipcMain.handle(IPC.sourceReportPlayback, (_e, sourceId: string, request: Parameters<SourcesApi["reportPlayback"]>[1]) => sources.reportPlayback(sourceId, request));
  ipcMain.handle(IPC.sourcePingOnline, (_e, sourceId: string) => sources.pingOnline(sourceId));
  ipcMain.handle(IPC.sourceListLibrary, (_e, sourceId: string) => sources.listLibrary(sourceId));
  ipcMain.handle(IPC.sourceSettingsRead, (_e, sourceId: string) => sources.settings.read(sourceId));
  ipcMain.handle(IPC.sourceSettingsWrite, (_e, sourceId: string, key: string, value: string | null) => sources.settings.write(sourceId, key, value));
  ipcMain.handle(IPC.sourceChallengeSolve, (_e, url: string) => sources.solveChallenge(url));
  ipcMain.handle(IPC.sourceSyncLibraryEntry, (_e, sourceId: string, request: Parameters<SourcesApi["syncLibraryEntry"]>[1]) =>
    sources.syncLibraryEntry(sourceId, request),
  );
}
