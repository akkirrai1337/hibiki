export const IPC = {
  sourcesList: "sources:list",
  sourceSearch: "source:search",
  sourceSearchCancel: "source:search:cancel",
  sourceLatest: "source:latest",
  sourceGetById: "source:getById",
  // Whatever is already on disk for a set of titles - lets a screen paint before the network
  // answers. See offlineCache.getCachedAnimeMany.
  sourceCachedTitles: "source:cachedTitles",
  sourceCachedPlaybackGroups: "source:cachedPlaybackGroups",
  sourcePlaybackGroups: "source:playbackGroups",
  sourcePlayerLinks: "source:playerLinks",
  sourceResolvePlayerLink: "source:resolvePlayerLink",
  sourceFilterCatalog: "source:filterCatalog",
  sourceRecommendations: "source:recommendations",

  // Account, and what an account unlocks. Each is answered only by sources that declare the
  // matching capability - see SourceCapability.
  sourceLogin: "source:account:login",
  sourceLoginWeb: "source:account:loginWeb",
  sourceLogout: "source:account:logout",
  sourceAccount: "source:account:get",
  sourceComments: "source:comments:list",
  sourcePostComment: "source:comments:post",
  sourceVoteComment: "source:comments:vote",
  sourceReviews: "source:reviews:list",
  sourcePostReview: "source:reviews:post",
  sourceSyncLibraryEntry: "source:library:sync",
  sourceListLibrary: "source:library:list",
  sourceReportPlayback: "source:activity:report",
  sourcePingOnline: "source:activity:ping",
  // Values for the settings rows a source declares. The same store the script reads, so a toggle
  // flipped here is a value the source can act on.
  sourceSettingsRead: "source:settings:read",
  sourceSettingsWrite: "source:settings:write",

  sourcesRepositoriesList: "sources:repositories:list",
  sourcesRepositoriesAdd: "sources:repositories:add",
  sourcesRepositoriesRemove: "sources:repositories:remove",
  sourcesMarketplaceFetch: "sources:marketplace:fetch",
  sourcesInstall: "sources:install",
  sourcesUninstall: "sources:uninstall",
  sourcesInstalledVersions: "sources:installedVersions",
  // Pushed main -> renderer whenever anything is installed or uninstalled, so no caller has to
  // remember to refresh what it just changed - which is exactly what went wrong before.
  sourcesChanged: "sources:changed",
  // The window that lets a person pass a source's Cloudflare check.
  sourceChallengeSolve: "sources:challenge:solve",
  sourcesMissing: "sources:missing",

  // The profile banner's bytes, written to disk (see main/ipc/profileBanner.ts) rather than kept
  // in the same localStorage-backed store as the rest of the profile - too big for that.
  profileSetBanner: "profile:setBanner",
  profileClearBanner: "profile:clearBanner",

  libraryList: "library:list",
  libraryUpsert: "library:upsert",
  libraryRemove: "library:remove",
  // A title's own rating, kept apart from the library for the same reason the table is (see
  // db/schema.ts): scoring a title and listing it are separate acts.
  ratingGet: "rating:get",
  ratingSet: "rating:set",

  progressGet: "progress:get",
  progressUpsert: "progress:upsert",
  progressListRecent: "progress:listRecent",
  progressListForAnime: "progress:listForAnime",
  progressListDailyActivity: "progress:listDailyActivity",
  progressRemoveForAnime: "progress:removeForAnime",
  progressRemoveEpisode: "progress:removeEpisode",
  progressSaveThumbnail: "progress:saveThumbnail",

  xpEventsList: "xpEvents:list",
  xpEventsRecord: "xpEvents:record",
  xpEventsClear: "xpEvents:clear",

  downloadsStart: "downloads:start",
  downloadsPause: "downloads:pause",
  downloadsResume: "downloads:resume",
  downloadsCancel: "downloads:cancel",
  downloadsList: "downloads:list",
  downloadsRemove: "downloads:remove",
  downloadsGetForEpisode: "downloads:getForEpisode",
  // Pushed main -> renderer (not a request/response handle) - the only channel in the app that
  // works this way, since a download's progress needs to keep reaching the renderer over the
  // whole transfer instead of resolving once like every other IPC call here does.
  downloadsProgress: "downloads:progress",

  playerRegisterHeaders: "player:registerHeaders",
  playerRegisterHeaderOrigin: "player:registerHeaderOrigin",
  playerUnregisterHeaders: "player:unregisterHeaders",
  playerResolveStreamUrl: "player:resolveStreamUrl",
  playerCaptureFrame: "player:captureFrame",

  discordSetEnabled: "discord:setEnabled",
  discordUpdatePresence: "discord:updatePresence",
  discordSetIdlePresence: "discord:setIdlePresence",
  discordClearPresence: "discord:clearPresence",

  windowUnmaximizeForDrag: "window:unmaximizeForDrag",
  windowMinimize: "window:minimize",
  windowToggleMaximize: "window:toggleMaximize",
  windowClose: "window:close",
  windowIsMaximized: "window:isMaximized",
  // Pushed main -> renderer (not a request/response handle), same idea as downloadsProgress above
  // - the window can flip between maximized/restored from several places the renderer never
  // directly asks for (double-clicking the title bar, dragging to a screen edge, Win+Up/Down, the
  // taskbar's own context menu), so the maximize/restore button's icon needs to stay in sync via a
  // push rather than only updating itself right after its own click.
  windowMaximizedChanged: "window:maximizedChanged",

  backupCreate: "backup:create",
  backupRestore: "backup:restore",
  appRelaunch: "app:relaunch",
  appGetHardwareAcceleration: "app:getHardwareAcceleration",
  appSetHardwareAcceleration: "app:setHardwareAcceleration",
  logsExport: "logs:export",
  logsRecent: "logs:recent",
  logsAppend: "logs:append",
  logsOpenFolder: "logs:openFolder",
  diagnosticsMemory: "diagnostics:memory",
  appGetVersion: "app:getVersion",
  // Pushed main -> renderer once the "Watch" button on a friend's Discord Rich Presence card opens
  // a "hibiki://watch/..." link (see main/deepLink.ts) and this window is ready to act on it.
  appDeepLinkWatch: "app:deepLinkWatch",

  trackingAccount: "tracking:account",
  trackingSignIn: "tracking:signIn",
  trackingSignOut: "tracking:signOut",
  trackingGetLink: "tracking:getLink",
  trackingSearch: "tracking:search",
  trackingSetLink: "tracking:setLink",
  trackingRemoveFromList: "tracking:removeFromList",
  trackingSetFavourite: "tracking:setFavourite",
  trackingImport: "tracking:import",
  // Pushed backend -> renderer: an account signed in or out, or a title's link changed.
  trackingChanged: "tracking:changed",
  // Pushed backend -> renderer while an import runs, same idea as downloadsProgress.
  trackingImportProgress: "tracking:importProgress",

  syncDevices: "sync:devices",
  syncRemove: "sync:remove",
  syncStartPairing: "sync:startPairing",
  syncStopPairing: "sync:stopPairing",
  syncDiscover: "sync:discover",
  syncPair: "sync:pair",
  syncNow: "sync:now",
  // Pushed main -> renderer: paired devices changed, or a sync brought data in.
  syncChanged: "sync:changed",

  updatesCheck: "updates:check",
  updatesDownloadAndInstall: "updates:downloadAndInstall",
  updatesOpenRelease: "updates:openRelease",
  // Pushed main -> renderer over the whole download, same shape as downloadsProgress above.
  updatesProgress: "updates:progress",
} as const;

export type IpcChannel = (typeof IPC)[keyof typeof IPC];
