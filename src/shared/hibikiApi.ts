// The contract of `window.hibiki`: what the renderer may ask of the backend. Shared so the
// Electron preload (src/preload/index.ts) is checked against it, and so another platform can
// implement the same surface without going through Electron IPC.
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
  SourceRecommendations,
  SyncCandidate,
  SyncPairMode,
  SyncDevice,
  DownloadProgress,
  DownloadRequest,
  InstalledVersions,
  RatingSyncResult,
  LibraryEntry,
  MarketplaceExtension,
  PlaybackGroup,
  PlayerLink,
  PlayerLinkPreference,
  RepositoryFetchResult,
  SearchFilterCatalog,
  SearchRequest,
  SourceAccount,
  SourceComment,
  SourceInfo,
  MissingSources,
  SourceLibraryEntry,
  SourceReview,
  TrackerAccount,
  TrackerId,
  TrackerImportProgress,
  TrackerImportReport,
  TrackerLink,
  TrackerMedia,
  UpdateDownloadProgress,
  WatchProgress,
  XpEvent,
  MemorySnapshot,
} from "@shared/types";

export interface LogEntry {
  time: number;
  level: "debug" | "info" | "warn" | "error";
  scope: string;
  message: string;
}

/** A button of the picture-in-picture window. */
export type PipAction = "previous" | "toggle" | "next" | "audioOnly";

export interface PipState {
  enabled: boolean;
  playing: boolean;
  hasPrevious: boolean;
  hasNext: boolean;
  /** The video's own size, for the window's shape. */
  width?: number;
  height?: number;
  /** The buttons' names, in the app's language. */
  labels: Record<"previous" | "next" | "play" | "pause" | "audioOnly", string>;
}

export interface HibikiApi {
  sources: {
    list(): Promise<SourceInfo[]>;
    search(sourceId: string, request: SearchRequest, requestId?: string): Promise<AnimeTitle[]>;
    cancelSearch(requestId: string): void;
    latest(sourceId: string, limit: number): Promise<AnimeTitle[]>;
    getById(sourceId: string, id: string): Promise<AnimeTitle>;
    cachedTitles(keys: Array<{ sourceId: string; animeId: string }>): Promise<Record<string, CachedAnimeEntry>>;
    cachedPlaybackGroups(sourceId: string, titleId: string): Promise<CachedPlaybackGroupsEntry | null>;
    playbackGroups(sourceId: string, titleId: string): Promise<PlaybackGroup[]>;
    playerLinks(sourceId: string, titleId: string, groupId: string, episodeId: string, preference?: PlayerLinkPreference): Promise<PlayerLink[]>;
    resolvePlayerLink(link: PlayerLink): Promise<PlayerLink[]>;
    filterCatalog(sourceId: string): Promise<SearchFilterCatalog>;
    /** What to watch next on this source, from what was watched on it. `sort` is the order its genre
     * searches use - the home screen's own. */
    recommendations(sourceId: string, sort?: string): Promise<SourceRecommendations>;
    // Account, and what an account unlocks. Answered only by sources declaring the matching
    // capability - see SourceCapability.
    account: {
      get(sourceId: string): Promise<SourceAccount | null>;
      login(sourceId: string, credentials: { login: string; password: string }): Promise<SourceAccount>;
      loginWeb(sourceId: string): Promise<SourceAccount>;
      logout(sourceId: string): Promise<void>;
    };
    comments: {
      list(sourceId: string, request: { animeId: string; parentId?: string | null; offset?: number }): Promise<SourceComment[]>;
      post(sourceId: string, request: { animeId: string; text: string; parentId?: string | null }): Promise<SourceComment>;
      /** 1 to like, -1 to dislike, 0 to take a vote back. */
      vote(sourceId: string, request: { commentId: string; vote: number }): Promise<boolean>;
    };
    reviews: {
      list(sourceId: string, request: { animeId: string; offset?: number }): Promise<SourceReview[]>;
      post(sourceId: string, request: { animeId: string; text: string; rating?: number | null }): Promise<SourceReview>;
    };
    // Values for the rows a source's manifest declares - the same store its script reads, so a
    // toggle flipped here is a value the source can act on. Declared keys only: a session token
    // lives in that store too, and this is not a way to read it.
    settings: {
      read(sourceId: string): Promise<Record<string, string>>;
      write(sourceId: string, key: string, value: string | null): Promise<void>;
    };
    listLibrary(sourceId: string): Promise<SourceLibraryEntry[]>;
    reportPlayback(sourceId: string, request: { videoId: string; positionSeconds: number; durationSeconds: number; watchedSeconds: number[] }): Promise<boolean>;
    pingOnline(sourceId: string): Promise<boolean>;
    syncLibraryEntry(sourceId: string, request: { animeId: string; category: string | null; rating?: number | null }): Promise<void>;
    repositories: {
      list(): Promise<string[]>;
      add(url: string): Promise<string[]>;
      remove(url: string): Promise<string[]>;
    };
    marketplace(urls: string[]): Promise<RepositoryFetchResult[]>;
    install(extension: MarketplaceExtension, originUrl: string): Promise<SourceInfo[]>;
    uninstall(id: string): Promise<SourceInfo[]>;
    installedVersions(): Promise<InstalledVersions>;
    onChanged(callback: () => void): () => void;
    /** Opens the site in a window for the person to pass its Cloudflare check (the page named by a
     * source error, see shared/cloudflare.ts); true once passed, false if the window was closed
     * first. Later requests to the site carry the clearance. */
    solveChallenge(url: string): Promise<boolean>;
    /** Sources the library, history and ratings here use that are not installed - offered after a
     * sync brings in another device's titles - with where each can be installed from. */
    missingSources(): Promise<MissingSources>;
  };
  ratings: {
    get(sourceId: string, animeId: string): Promise<number | null>;
    set(sourceId: string, animeId: string, rating: number | null): Promise<RatingSyncResult>;
  };
  profile: {
    setBanner(bytes: ArrayBuffer, mimeType: string): Promise<string>;
    clearBanner(): Promise<void>;
    /**
     * Where the renderer loads a banner from, for a platform without desktop's hibiki-profile://
     * protocol (Android). Absent on desktop.
     */
    bannerUrl?(filename: string): string;
  };
  library: {
    list(): Promise<LibraryEntry[]>;
    upsert(entry: LibraryEntry): Promise<void>;
    remove(sourceId: string, animeId: string): Promise<void>;
  };
  progress: {
    get(sourceId: string, titleId: string, episodeId: string): Promise<WatchProgress | null>;
    upsert(progress: WatchProgress): Promise<void>;
    listRecent(limit: number): Promise<WatchProgress[]>;
    listForAnime(sourceId: string, titleId: string): Promise<WatchProgress[]>;
    listDailyActivity(days: number): Promise<DailyActivity[]>;
    removeForAnime(sourceId: string, titleId: string): Promise<void>;
    removeEpisode(sourceId: string, titleId: string, episodeId: string): Promise<void>;
    saveThumbnail(sourceId: string, titleId: string, episodeId: string, dataUrl: string): Promise<void>;
  };
  player: {
    registerHeaders(url: string, headers: Record<string, string> | null | undefined): Promise<string>;
    registerHeaderOrigin(sessionId: string, url: string): Promise<boolean>;
    unregisterHeaders(sessionId: string): Promise<void>;
    resolveStreamUrl(url: string, headers: Record<string, string> | null | undefined): Promise<string>;
    /** Only on hosts that play through a stream proxy (Android): the URL the player should request
     * for `url` under a session from registerHeaders. Absent on desktop, where headers are injected
     * at the session level and URLs are loaded as they are. */
    streamUrl?(sessionId: string, url: string): string;
    captureFrame(rect: { x: number; y: number; width: number; height: number }): Promise<string | null>;
  };
  discord: {
    setEnabled(enabled: boolean): Promise<void>;
    updatePresence(presence: DiscordPresence): Promise<void>;
    setIdlePresence(): Promise<void>;
    clearPresence(): Promise<void>;
  };
  window: {
    unmaximizeForDrag(cursorX: number): void;
    minimize(): void;
    toggleMaximize(): void;
    close(): void;
    isMaximized(): Promise<boolean>;
    onMaximizedChanged(callback: (maximized: boolean) => void): () => void;
  };
  /**
   * Syncing the library, progress, ratings and xp between a computer and a phone on the same network
   * (core/sync). The computer is the one that waits (pairing, answering); the phone finds it, pairs
   * with its code and syncs. Each side only has its own half of the methods.
   */
  sync: {
    devices(): Promise<SyncDevice[]>;
    remove(deviceId: string): Promise<void>;
    /** Something changed: the paired devices, or data a sync brought in. */
    onChanged(callback: (what: "devices" | "data") => void): () => void;
    /** Computer: open a pairing window and get the code to show. */
    startPairing?(): Promise<{ code: string; expiresAt: number }>;
    stopPairing?(): Promise<void>;
    /** Computers running hibiki on this network (other than this one). */
    discover?(): Promise<SyncCandidate[]>;
    /** Pair with a found computer using its code. Rejects with a message ending in the error code ("bad-code"...). */
    pair?(candidate: SyncCandidate, code: string, mode: SyncPairMode): Promise<SyncDevice>;
    /** Sync now with the computers this device reaches out to. */
    syncNow?(): Promise<void>;
  };
  updates: {
    check(): Promise<AppUpdate | null>;
    downloadAndInstall(update: AppUpdate): Promise<void>;
    openRelease(url: string): Promise<void>;
    onProgress(callback: (progress: UpdateDownloadProgress) => void): () => void;
    /**
     * Android only: the system's "install unknown apps" permission, which an in-app update needs.
     * `downloadAndInstall` fails with code "permission-needed" without it; asking is the UI's job,
     * since it can explain why first.
     */
    installPermission?: { granted(): Promise<boolean>; request(): Promise<boolean> };
  };
  zoom: {
    set(factor: number): void;
    get(): number;
  };
  platform: NodeJS.Platform;
  /**
   * The phone the app runs on: present only on Android, where the mobile layout uses it. Desktop has
   * no such surface (its window chrome lives in `window` above).
   */
  device?: {
    /** The system Back key/gesture. The app decides what it does; see lib/backButton.ts. */
    onBack(callback: () => void): () => void;
    /** Leaves to the launcher, keeping the app alive - Back on the first screen. */
    minimize(): void;
    setSystemBars(options: { hidden?: boolean; style?: "light" | "dark" }): Promise<void>;
    keepAwake(on: boolean): Promise<void>;
    setOrientation(orientation: "landscape" | "portrait" | "sensor" | "auto"): Promise<void>;
    /**
     * Work that has to go on with the app in the background (downloads): shown as a notification,
     * and the app kept alive while it runs. null ends it.
     */
    setBackgroundWork(work: { title: string; text?: string; progress?: number } | null): Promise<void>;
    /** Picture in picture - the player in a small window over other apps. Absent where unsupported. */
    pip?: {
      /** The player's state: the window's buttons and shape; enabled while a video is on screen. */
      update(state: PipState): void;
      enter(): Promise<boolean>;
      onAction(callback: (action: PipAction) => void): () => void;
      onModeChange(callback: (active: boolean) => void): () => void;
    };
  };
  app: {
    getVersion(): Promise<string>;
    relaunch(): void;
    getHardwareAcceleration(): Promise<boolean>;
    /** Takes effect on the next start. */
    setHardwareAcceleration(enabled: boolean): Promise<void>;
    onDeepLinkWatch(callback: (target: DeepLinkWatchTarget) => void): () => void;
  };
  logs: {
    /** Resolves the written path, or null if the save dialog was cancelled (Android: handed to the share sheet, always null). */
    export(): Promise<string | null>;
    recent(limit?: number): Promise<LogEntry[]>;
    openFolder(): void;
    /** Per-process memory; `record` also writes it into the log. */
    memory(record?: boolean): Promise<MemorySnapshot>;
    append(level: LogEntry["level"], scope: string, message: string): void;
  };
  backup: {
    create(localStorageEntries: Record<string, string>): Promise<string | null>;
    restore(): Promise<{ localStorage: Record<string, string> } | null>;
  };
  /**
   * List sites the library is mirrored to (AniList). Once signed in it runs by itself: a finished
   * episode raises the progress there, a category picked here sets the status there. Only forward,
   * and nothing is ever deleted on the tracker.
   */
  tracking: {
    account(tracker: TrackerId): Promise<TrackerAccount>;
    /** Opens the tracker's sign-in page in the system browser; the account arrives through onChanged. */
    signIn(tracker: TrackerId): Promise<void>;
    signOut(tracker: TrackerId): Promise<void>;
    /** The title's link with the account's entry for it; a title in the library is linked first. */
    getLink(tracker: TrackerId, sourceId: string, animeId: string): Promise<TrackerLink | null>;
    search(tracker: TrackerId, query: string): Promise<TrackerMedia[]>;
    /** Links the title to `mediaId` by hand, or unlinks it (null). */
    setLink(tracker: TrackerId, sourceId: string, animeId: string, mediaId: number | null): Promise<TrackerLink | null>;
    /** Deletes the title's entry from the account's list and stops syncing the title (it is unlinked). */
    removeFromList(tracker: TrackerId, sourceId: string, animeId: string): Promise<TrackerLink | null>;
    setFavourite(tracker: TrackerId, sourceId: string, animeId: string, favourite: boolean): Promise<TrackerLink | null>;
    /** Brings the account's lists into the library, finding each title on `sourceId`. */
    importLibrary(tracker: TrackerId, sourceId: string): Promise<TrackerImportReport>;
    onImportProgress(callback: (progress: TrackerImportProgress) => void): () => void;
    onChanged(callback: () => void): () => void;
  };
  xp: {
    list(): Promise<XpEvent[]>;
    record(kind: string, xp: number, createdAt: number): Promise<void>;
    clear(): Promise<void>;
  };
  downloads: {
    start(request: DownloadRequest): Promise<void>;
    pause(episodeId: string): Promise<void>;
    resume(episodeId: string): Promise<void>;
    cancel(episodeId: string): Promise<void>;
    list(): Promise<DownloadedEpisode[]>;
    remove(sourceId: string, animeId: string, episodeId: string): Promise<void>;
    getForEpisode(sourceId: string, animeId: string, episodeId: string): Promise<DownloadedEpisodeFile | null>;
    /** Where the page loads a downloaded file from, where that is not the desktop's
     * `hibiki-download:` scheme (Android: its local server). */
    fileUrl?(filePath: string): string;
    onProgress(callback: (progress: DownloadProgress) => void): () => void;
  };
}
