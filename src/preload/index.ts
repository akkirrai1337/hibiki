import { contextBridge, ipcRenderer, webFrame } from "electron";
import { IPC } from "@shared/ipc";
import type { HibikiApi, LogEntry } from "@shared/hibikiApi";
import type {
  AnimeTitle,
  AppUpdate,
  CachedAnimeEntry,
  CachedPlaybackGroupsEntry,
  DailyActivity,
  DeepLinkWatchTarget,
  DiscordPresence,
  DownloadedEpisode,
  DownloadedEpisodeFile,
  DownloadProgress,
  DownloadRequest,
  InstalledVersions,
  LibraryEntry,
  MarketplaceExtension,
  PlaybackGroup,
  PlayerLink,
  PlayerLinkPreference,
  RepositoryFetchResult,
  SearchFilterCatalog,
  SourceRecommendations,
  SyncCandidate,
  SyncPairMode,
  SyncDevice,
  SearchRequest,
  SourceAccount,
  SourceComment,
  SourceInfo,
  MissingSources,
  SourceLibraryEntry,
  SourceReview,
  UpdateDownloadProgress,
  WatchProgress,
  RatingSyncResult,
  XpEvent,
  MemorySnapshot,
  TrackerAccount,
  TrackerId,
  TrackerImportProgress,
  TrackerImportReport,
  TrackerLink,
  TrackerMedia,
} from "@shared/types";

const api: HibikiApi = {
  sources: {
    list: (): Promise<SourceInfo[]> => ipcRenderer.invoke(IPC.sourcesList),
    search: (sourceId: string, request: SearchRequest, requestId?: string): Promise<AnimeTitle[]> =>
      ipcRenderer.invoke(IPC.sourceSearch, sourceId, request, requestId),
    cancelSearch: (requestId: string): void => ipcRenderer.send(IPC.sourceSearchCancel, requestId),
    latest: (sourceId: string, limit: number): Promise<AnimeTitle[]> =>
      ipcRenderer.invoke(IPC.sourceLatest, sourceId, limit),
    getById: (sourceId: string, id: string): Promise<AnimeTitle> =>
      ipcRenderer.invoke(IPC.sourceGetById, sourceId, id),
    cachedTitles: (keys: Array<{ sourceId: string; animeId: string }>): Promise<Record<string, CachedAnimeEntry>> =>
      ipcRenderer.invoke(IPC.sourceCachedTitles, keys),
    cachedPlaybackGroups: (sourceId: string, titleId: string): Promise<CachedPlaybackGroupsEntry | null> =>
      ipcRenderer.invoke(IPC.sourceCachedPlaybackGroups, sourceId, titleId),
    playbackGroups: (sourceId: string, titleId: string): Promise<PlaybackGroup[]> =>
      ipcRenderer.invoke(IPC.sourcePlaybackGroups, sourceId, titleId),
    playerLinks: (sourceId: string, titleId: string, groupId: string, episodeId: string, preference?: PlayerLinkPreference): Promise<PlayerLink[]> =>
      ipcRenderer.invoke(IPC.sourcePlayerLinks, sourceId, titleId, groupId, episodeId, preference),
    resolvePlayerLink: (link: PlayerLink): Promise<PlayerLink[]> =>
      ipcRenderer.invoke(IPC.sourceResolvePlayerLink, link),
    filterCatalog: (sourceId: string): Promise<SearchFilterCatalog> =>
      ipcRenderer.invoke(IPC.sourceFilterCatalog, sourceId),
    recommendations: (sourceId: string, sort?: string): Promise<SourceRecommendations> =>
      ipcRenderer.invoke(IPC.sourceRecommendations, sourceId, sort),
    account: {
      get: (sourceId: string): Promise<SourceAccount | null> => ipcRenderer.invoke(IPC.sourceAccount, sourceId),
      login: (sourceId: string, credentials: { login: string; password: string }): Promise<SourceAccount> =>
        ipcRenderer.invoke(IPC.sourceLogin, sourceId, credentials),
      loginWeb: (sourceId: string): Promise<SourceAccount> => ipcRenderer.invoke(IPC.sourceLoginWeb, sourceId),
      logout: (sourceId: string): Promise<void> => ipcRenderer.invoke(IPC.sourceLogout, sourceId),
    },
    comments: {
      list: (sourceId: string, request: { animeId: string; parentId?: string | null; offset?: number }): Promise<SourceComment[]> =>
        ipcRenderer.invoke(IPC.sourceComments, sourceId, request),
      post: (sourceId: string, request: { animeId: string; text: string; parentId?: string | null }): Promise<SourceComment> =>
        ipcRenderer.invoke(IPC.sourcePostComment, sourceId, request),
      /** 1 to like, -1 to dislike, 0 to take a vote back. */
      vote: (sourceId: string, request: { commentId: string; vote: number }): Promise<boolean> =>
        ipcRenderer.invoke(IPC.sourceVoteComment, sourceId, request),
    },
    reviews: {
      list: (sourceId: string, request: { animeId: string; offset?: number }): Promise<SourceReview[]> =>
        ipcRenderer.invoke(IPC.sourceReviews, sourceId, request),
      post: (sourceId: string, request: { animeId: string; text: string; rating?: number | null }): Promise<SourceReview> =>
        ipcRenderer.invoke(IPC.sourcePostReview, sourceId, request),
    },
    settings: {
      read: (sourceId: string): Promise<Record<string, string>> =>
        ipcRenderer.invoke(IPC.sourceSettingsRead, sourceId),
      write: (sourceId: string, key: string, value: string | null): Promise<void> =>
        ipcRenderer.invoke(IPC.sourceSettingsWrite, sourceId, key, value),
    },
    reportPlayback: (
      sourceId: string,
      request: { videoId: string; positionSeconds: number; durationSeconds: number; watchedSeconds: number[] },
    ): Promise<boolean> => ipcRenderer.invoke(IPC.sourceReportPlayback, sourceId, request),
    pingOnline: (sourceId: string): Promise<boolean> => ipcRenderer.invoke(IPC.sourcePingOnline, sourceId),
    listLibrary: (sourceId: string): Promise<SourceLibraryEntry[]> =>
      ipcRenderer.invoke(IPC.sourceListLibrary, sourceId),
    syncLibraryEntry: (
      sourceId: string,
      request: { animeId: string; category: string | null; rating?: number | null },
    ): Promise<void> => ipcRenderer.invoke(IPC.sourceSyncLibraryEntry, sourceId, request),
    repositories: {
      list: (): Promise<string[]> => ipcRenderer.invoke(IPC.sourcesRepositoriesList),
      add: (url: string): Promise<string[]> => ipcRenderer.invoke(IPC.sourcesRepositoriesAdd, url),
      remove: (url: string): Promise<string[]> => ipcRenderer.invoke(IPC.sourcesRepositoriesRemove, url),
    },
    marketplace: (urls: string[]): Promise<RepositoryFetchResult[]> =>
      ipcRenderer.invoke(IPC.sourcesMarketplaceFetch, urls),
    install: (extension: MarketplaceExtension, originUrl: string): Promise<SourceInfo[]> =>
      ipcRenderer.invoke(IPC.sourcesInstall, extension, originUrl),
    uninstall: (id: string): Promise<SourceInfo[]> => ipcRenderer.invoke(IPC.sourcesUninstall, id),
    // Installed player-resolver versions, keyed by id. Resolvers are hidden dependencies and
    // never appear in list(), but the Sources screen needs them to spot a resolver update.
    installedVersions: (): Promise<InstalledVersions> => ipcRenderer.invoke(IPC.sourcesInstalledVersions),
    onChanged: (callback: () => void): (() => void) => {
      const listener = () => callback();
      ipcRenderer.on(IPC.sourcesChanged, listener);
      return () => ipcRenderer.removeListener(IPC.sourcesChanged, listener);
    },
    solveChallenge: (url: string): Promise<boolean> => ipcRenderer.invoke(IPC.sourceChallengeSolve, url),
    missingSources: (): Promise<MissingSources> => ipcRenderer.invoke(IPC.sourcesMissing),
  },
  sync: {
    devices: (): Promise<SyncDevice[]> => ipcRenderer.invoke(IPC.syncDevices),
    remove: (deviceId: string): Promise<void> => ipcRenderer.invoke(IPC.syncRemove, deviceId),
    startPairing: (): Promise<{ code: string; expiresAt: number }> => ipcRenderer.invoke(IPC.syncStartPairing),
    stopPairing: (): Promise<void> => ipcRenderer.invoke(IPC.syncStopPairing),
    discover: (): Promise<SyncCandidate[]> => ipcRenderer.invoke(IPC.syncDiscover),
    pair: (candidate: SyncCandidate, code: string, mode: SyncPairMode): Promise<SyncDevice> => ipcRenderer.invoke(IPC.syncPair, candidate, code, mode),
    syncNow: (): Promise<void> => ipcRenderer.invoke(IPC.syncNow),
    onChanged: (callback: (what: "devices" | "data") => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, payload: { what: "devices" | "data" }) => callback(payload.what);
      ipcRenderer.on(IPC.syncChanged, listener);
      return () => ipcRenderer.removeListener(IPC.syncChanged, listener);
    },
  },
  tracking: {
    account: (tracker: TrackerId): Promise<TrackerAccount> => ipcRenderer.invoke(IPC.trackingAccount, tracker),
    signIn: (tracker: TrackerId): Promise<void> => ipcRenderer.invoke(IPC.trackingSignIn, tracker),
    signOut: (tracker: TrackerId): Promise<void> => ipcRenderer.invoke(IPC.trackingSignOut, tracker),
    getLink: (tracker: TrackerId, sourceId: string, animeId: string): Promise<TrackerLink | null> =>
      ipcRenderer.invoke(IPC.trackingGetLink, tracker, sourceId, animeId),
    search: (tracker: TrackerId, query: string): Promise<TrackerMedia[]> => ipcRenderer.invoke(IPC.trackingSearch, tracker, query),
    setLink: (tracker: TrackerId, sourceId: string, animeId: string, mediaId: number | null): Promise<TrackerLink | null> =>
      ipcRenderer.invoke(IPC.trackingSetLink, tracker, sourceId, animeId, mediaId),
    removeFromList: (tracker: TrackerId, sourceId: string, animeId: string): Promise<TrackerLink | null> =>
      ipcRenderer.invoke(IPC.trackingRemoveFromList, tracker, sourceId, animeId),
    setFavourite: (tracker: TrackerId, sourceId: string, animeId: string, favourite: boolean): Promise<TrackerLink | null> =>
      ipcRenderer.invoke(IPC.trackingSetFavourite, tracker, sourceId, animeId, favourite),
    importLibrary: (tracker: TrackerId, sourceId: string): Promise<TrackerImportReport> =>
      ipcRenderer.invoke(IPC.trackingImport, tracker, sourceId),
    onImportProgress: (callback: (progress: TrackerImportProgress) => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, progress: TrackerImportProgress) => callback(progress);
      ipcRenderer.on(IPC.trackingImportProgress, listener);
      return () => ipcRenderer.removeListener(IPC.trackingImportProgress, listener);
    },
    onChanged: (callback: () => void): (() => void) => {
      const listener = () => callback();
      ipcRenderer.on(IPC.trackingChanged, listener);
      return () => ipcRenderer.removeListener(IPC.trackingChanged, listener);
    },
  },
  ratings: {
    get: (sourceId: string, animeId: string): Promise<number | null> => ipcRenderer.invoke(IPC.ratingGet, sourceId, animeId),
    /** null clears it. Resolves once the source's account has been told, when it can be. */
    set: (sourceId: string, animeId: string, rating: number | null): Promise<RatingSyncResult> =>
      ipcRenderer.invoke(IPC.ratingSet, sourceId, animeId, rating),
  },
  profile: {
    /** Writes the banner's bytes to disk and returns its filename (see profileBannerUrl in lib/hibiki.ts). */
    setBanner: (bytes: ArrayBuffer, mimeType: string): Promise<string> => ipcRenderer.invoke(IPC.profileSetBanner, bytes, mimeType),
    clearBanner: (): Promise<void> => ipcRenderer.invoke(IPC.profileClearBanner),
  },
  library: {
    list: (): Promise<LibraryEntry[]> => ipcRenderer.invoke(IPC.libraryList),
    upsert: (entry: LibraryEntry): Promise<void> => ipcRenderer.invoke(IPC.libraryUpsert, entry),
    remove: (sourceId: string, animeId: string): Promise<void> =>
      ipcRenderer.invoke(IPC.libraryRemove, sourceId, animeId),
  },
  progress: {
    get: (sourceId: string, titleId: string, episodeId: string): Promise<WatchProgress | null> =>
      ipcRenderer.invoke(IPC.progressGet, sourceId, titleId, episodeId),
    upsert: (progress: WatchProgress): Promise<void> => ipcRenderer.invoke(IPC.progressUpsert, progress),
    listRecent: (limit: number): Promise<WatchProgress[]> => ipcRenderer.invoke(IPC.progressListRecent, limit),
    listForAnime: (sourceId: string, titleId: string): Promise<WatchProgress[]> =>
      ipcRenderer.invoke(IPC.progressListForAnime, sourceId, titleId),
    listDailyActivity: (days: number): Promise<DailyActivity[]> =>
      ipcRenderer.invoke(IPC.progressListDailyActivity, days),
    removeForAnime: (sourceId: string, titleId: string): Promise<void> =>
      ipcRenderer.invoke(IPC.progressRemoveForAnime, sourceId, titleId),
    removeEpisode: (sourceId: string, titleId: string, episodeId: string): Promise<void> =>
      ipcRenderer.invoke(IPC.progressRemoveEpisode, sourceId, titleId, episodeId),
    saveThumbnail: (sourceId: string, titleId: string, episodeId: string, dataUrl: string): Promise<void> =>
      ipcRenderer.invoke(IPC.progressSaveThumbnail, sourceId, titleId, episodeId, dataUrl),
  },
  player: {
    // Referer (and other forbidden-by-spec headers) can't be set from renderer JS via
    // XHR/fetch/hls.js's xhrSetup, and a plain <video src> offers no header hook at all — this
    // registers them with the main process, which injects them at the session level instead.
    registerHeaders: (url: string, headers: Record<string, string> | null | undefined): Promise<string> =>
      ipcRenderer.invoke(IPC.playerRegisterHeaders, url, headers ?? null),
    registerHeaderOrigin: (sessionId: string, url: string): Promise<boolean> =>
      ipcRenderer.invoke(IPC.playerRegisterHeaderOrigin, sessionId, url),
    unregisterHeaders: (sessionId: string): Promise<void> =>
      ipcRenderer.invoke(IPC.playerUnregisterHeaders, sessionId),
    // Where a stream URL actually lands after its CDN's redirects - see main/playerStream.ts.
    // Resolves the input unchanged if it doesn't redirect or can't be checked.
    resolveStreamUrl: (url: string, headers: Record<string, string> | null | undefined): Promise<string> =>
      ipcRenderer.invoke(IPC.playerResolveStreamUrl, url, headers ?? null),
    // A real screenshot of the given on-screen rect (see main/index.ts's handler for why this
    // beats a renderer-side <canvas> capture) - null if the window's gone or the rect is empty.
    captureFrame: (rect: { x: number; y: number; width: number; height: number }): Promise<string | null> =>
      ipcRenderer.invoke(IPC.playerCaptureFrame, rect),
  },
  discord: {
    setEnabled: (enabled: boolean): Promise<void> => ipcRenderer.invoke(IPC.discordSetEnabled, enabled),
    updatePresence: (presence: DiscordPresence): Promise<void> => ipcRenderer.invoke(IPC.discordUpdatePresence, presence),
    setIdlePresence: (): Promise<void> => ipcRenderer.invoke(IPC.discordSetIdlePresence),
    clearPresence: (): Promise<void> => ipcRenderer.invoke(IPC.discordClearPresence),
  },
  window: {
    // Deliberately sendSync, not invoke - see the comment on the main-process handler for why
    // this needs to block and complete before returning control to Chromium's own drag handling.
    unmaximizeForDrag: (cursorX: number): void => {
      ipcRenderer.sendSync(IPC.windowUnmaximizeForDrag, cursorX);
    },
    minimize: (): void => { ipcRenderer.send(IPC.windowMinimize); },
    toggleMaximize: (): void => { ipcRenderer.send(IPC.windowToggleMaximize); },
    close: (): void => { ipcRenderer.send(IPC.windowClose); },
    isMaximized: (): Promise<boolean> => ipcRenderer.invoke(IPC.windowIsMaximized),
    onMaximizedChanged: (callback: (maximized: boolean) => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, maximized: boolean) => callback(maximized);
      ipcRenderer.on(IPC.windowMaximizedChanged, listener);
      return () => ipcRenderer.removeListener(IPC.windowMaximizedChanged, listener);
    },
  },
  // Not IPC: zoom belongs to this frame, and webFrame is only reachable from a preload script -
  // the renderer can't import electron, and routing it through the main process would add a round
  // trip to something that is a local, synchronous property of the window.
  updates: {
    check: (): Promise<AppUpdate | null> => ipcRenderer.invoke(IPC.updatesCheck),
    // Resolves only if the update failed: on success the app is quitting to let the installer run.
    downloadAndInstall: (update: AppUpdate): Promise<void> => ipcRenderer.invoke(IPC.updatesDownloadAndInstall, update),
    openRelease: (url: string): Promise<void> => ipcRenderer.invoke(IPC.updatesOpenRelease, url),
    onProgress: (callback: (progress: UpdateDownloadProgress) => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, progress: UpdateDownloadProgress) => callback(progress);
      ipcRenderer.on(IPC.updatesProgress, listener);
      return () => ipcRenderer.removeListener(IPC.updatesProgress, listener);
    },
  },
  zoom: {
    set: (factor: number): void => { webFrame.setZoomFactor(factor); },
    get: (): number => webFrame.getZoomFactor(),
  },
  platform: process.platform,
  app: {
    getVersion: (): Promise<string> => ipcRenderer.invoke(IPC.appGetVersion),
    relaunch: (): void => { ipcRenderer.send(IPC.appRelaunch); },
    getHardwareAcceleration: (): Promise<boolean> => ipcRenderer.invoke(IPC.appGetHardwareAcceleration),
    setHardwareAcceleration: (enabled: boolean): Promise<void> => ipcRenderer.invoke(IPC.appSetHardwareAcceleration, enabled),
    // A friend's "Watch" button on this app's own Discord Rich Presence card (see discordRpc.ts)
    // opened a "hibiki://watch/..." link, and main finished parsing and routing it here.
    onDeepLinkWatch: (callback: (target: DeepLinkWatchTarget) => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, target: DeepLinkWatchTarget) => callback(target);
      ipcRenderer.on(IPC.appDeepLinkWatch, listener);
      return () => ipcRenderer.removeListener(IPC.appDeepLinkWatch, listener);
    },
  },
  // Diagnostics: the renderer both writes into the shared log (its own playback/resolve failures,
  // which main never sees) and reads it back for the Settings preview / "Export log".
  logs: {
    export: (): Promise<string | null> => ipcRenderer.invoke(IPC.logsExport),
    recent: (limit?: number): Promise<LogEntry[]> =>
      ipcRenderer.invoke(IPC.logsRecent, limit),
    openFolder: (): void => { ipcRenderer.send(IPC.logsOpenFolder); },
    memory: (record?: boolean): Promise<MemorySnapshot> => ipcRenderer.invoke(IPC.diagnosticsMemory, record),
    append: (level: "debug" | "info" | "warn" | "error", scope: string, message: string): void => {
      ipcRenderer.send(IPC.logsAppend, level, scope, message);
    },
  },
  backup: {
    // Only the renderer can read its own localStorage (the persisted zustand stores - theme,
    // accent color, profile name, sidebar width, ...) - the main process has no access to it, so
    // it travels along as a plain argument here rather than main reaching in for it itself.
    create: (localStorageEntries: Record<string, string>): Promise<string | null> =>
      ipcRenderer.invoke(IPC.backupCreate, localStorageEntries),
    // Resolves null if the user cancelled the file picker, otherwise the localStorage entries to
    // write back - the caller is responsible for actually applying them (see backup.ts's
    // RestoreResult) since only the renderer can write its own localStorage.
    restore: (): Promise<{ localStorage: Record<string, string> } | null> => ipcRenderer.invoke(IPC.backupRestore),
  },
  xp: {
    list: (): Promise<XpEvent[]> => ipcRenderer.invoke(IPC.xpEventsList),
    record: (kind: string, xp: number, createdAt: number): Promise<void> =>
      ipcRenderer.invoke(IPC.xpEventsRecord, kind, xp, createdAt),
    clear: (): Promise<void> => ipcRenderer.invoke(IPC.xpEventsClear),
  },
  downloads: {
    start: (request: DownloadRequest): Promise<void> => ipcRenderer.invoke(IPC.downloadsStart, request),
    pause: (episodeId: string): Promise<void> => ipcRenderer.invoke(IPC.downloadsPause, episodeId),
    resume: (episodeId: string): Promise<void> => ipcRenderer.invoke(IPC.downloadsResume, episodeId),
    cancel: (episodeId: string): Promise<void> => ipcRenderer.invoke(IPC.downloadsCancel, episodeId),
    list: (): Promise<DownloadedEpisode[]> => ipcRenderer.invoke(IPC.downloadsList),
    remove: (sourceId: string, animeId: string, episodeId: string): Promise<void> =>
      ipcRenderer.invoke(IPC.downloadsRemove, sourceId, animeId, episodeId),
    getForEpisode: (sourceId: string, animeId: string, episodeId: string): Promise<DownloadedEpisodeFile | null> =>
      ipcRenderer.invoke(IPC.downloadsGetForEpisode, sourceId, animeId, episodeId),
    // Returns an unsubscribe function - the one listener-based API in this bridge (everything
    // else here is a one-shot invoke/response), since a download's progress has to keep reaching
    // the renderer over the whole transfer instead of resolving once like every other call does.
    onProgress: (callback: (progress: DownloadProgress) => void): (() => void) => {
      const listener = (_e: Electron.IpcRendererEvent, progress: DownloadProgress) => callback(progress);
      ipcRenderer.on(IPC.downloadsProgress, listener);
      return () => ipcRenderer.removeListener(IPC.downloadsProgress, listener);
    },
  },
};

contextBridge.exposeInMainWorld("hibiki", api);
