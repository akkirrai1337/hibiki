// What the shared backend needs from the platform it runs on - Electron on Windows today, a
// Capacitor WebView on Android next (see docs/android-port-plan.md). Everything here is either a
// thin transport (bytes over HTTP, a file, a SQLite connection) or a host capability with no
// portable equivalent (a hidden browser page, a native player proxy). Logic that decides *what* to
// request or store - caching, retries, resolver ordering, download queues - stays out of this file
// and is shared, so both platforms behave the same by construction.
//
// The interfaces grow as Phase 2 moves modules into the shared core: each extraction replaces a
// direct electron/node import with one of these ports, and adds what that module turns out to need.
import type { BaseSQLiteDatabase } from "drizzle-orm/sqlite-core";
import type { PlayerLink, SourceInfo, SubtitleTrack } from "@shared/types";
import type * as schema from "../core/db/schema";

export type PlatformKind = "electron" | "android";

/** Features that exist on one platform only. The UI hides what a platform cannot do instead of
 * branching on the platform name. */
export interface Capabilities {
  /** Custom title bar buttons (minimize/maximize/close) and window dragging. */
  windowControls: boolean;
  /** Page zoom of the app window. */
  zoom: boolean;
  discordPresence: boolean;
  /** A restart-to-apply GPU acceleration toggle. */
  hardwareAccelerationToggle: boolean;
  /** How an app update is applied: a Windows installer, an Android package, or not at all. */
  appUpdates: "installer" | "apk" | "none";
  /** "Open the log folder" in the system file manager. */
  revealLogFolder: boolean;
}

/** Absolute locations of everything the app persists. All derive from one per-user data root. */
export interface PlatformPaths {
  userData: string;
  database: string;
  extensions: string;
  extensionStorage: string;
  downloads: string;
  profile: string;
}

export interface FileStat {
  size: number;
  mtimeMs: number;
  isDirectory: boolean;
}

/** Async file access by absolute path. Paths are platform strings; build them with `join`. */
export interface FilesPort {
  join(...parts: string[]): string;
  exists(path: string): Promise<boolean>;
  /** null when nothing exists at `path`. */
  stat(path: string): Promise<FileStat | null>;
  /** Entry names (not paths) in `dir`; empty when the directory does not exist. */
  list(dir: string): Promise<string[]>;
  readText(path: string): Promise<string>;
  writeText(path: string, text: string): Promise<void>;
  readBytes(path: string): Promise<Uint8Array>;
  writeBytes(path: string, data: Uint8Array): Promise<void>;
  /** Creates parent directories as needed. */
  mkdir(dir: string): Promise<void>;
  /** No-op when nothing exists at `path`. Directories are removed with their contents. */
  remove(path: string): Promise<void>;
  rename(from: string, to: string): Promise<void>;
}

/** OS-backed encryption for small secrets at rest (source session tokens): DPAPI via Electron's
 * safeStorage on Windows, the Android Keystore on Android. */
export interface SecureStorePort {
  isAvailable(): Promise<boolean>;
  /** Base64 ciphertext. */
  encrypt(plaintext: string): Promise<string>;
  decrypt(ciphertext: string): Promise<string>;
}

export interface HttpRequest {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: string;
  /** "follow" (default) walks redirects; "manual" answers with the 3xx itself. */
  redirect?: "follow" | "manual";
  timeoutMs?: number;
  signal?: AbortSignal;
}

export interface HttpResponse {
  status: number;
  /** Where the request ended up after redirects. */
  url: string;
  /** Lower-cased names. Each header is one joined value, except set-cookie, which keeps one entry
   * per cookie. */
  headers: Record<string, string[]>;
  body: string;
}

/**
 * Plain HTTP with no browser rules: no CORS, any header may be set (Referer, Cookie, User-Agent),
 * and no cookie jar - cookies travel only when the caller sends them, exactly as with Node's fetch.
 * Bodies are decompressed. Rejects only when no response arrived at all.
 */
export interface HttpPort {
  request(request: HttpRequest): Promise<HttpResponse>;
}

export interface FetchToFileRequest {
  url: string;
  headers?: Record<string, string>;
  filePath: string;
  /** Whether to append to an existing file - decided per response, since a resumed download only
   * appends when the server honoured the Range request. */
  append: boolean | ((response: { status: number; headers: Record<string, string[]> }) => boolean);
  /** Write nothing until the whole body has arrived, so an abort mid-transfer leaves the file as it
   * was. For small pieces (HLS segments) that are retried whole on resume; a large progressive
   * download streams instead and tracks its own resume point. */
  buffered?: boolean;
  signal?: AbortSignal;
  onProgress?: (receivedBytes: number, totalBytes: number | null) => void;
}

/** Streams a response body straight to disk, never through a JS string. */
export interface DownloadTransferPort {
  fetchToFile(request: FetchToFileRequest): Promise<{ status: number; headers: Record<string, string[]>; bytesWritten: number }>;
}

/** A drizzle database over the app schema, sync (better-sqlite3) or async (Android). Shared code
 * always awaits its queries, which works for both. */
export type HibikiDatabase = BaseSQLiteDatabase<"sync" | "async", unknown, typeof schema>;

export interface DbPort {
  /** Opens (once) and migrates the database. */
  get(): HibikiDatabase;
  /** Folds pending writes into the main file so it can be copied as one self-contained snapshot. */
  checkpoint(): Promise<void>;
  /** Releases the file - before a backup restore overwrites it. The next get() reopens. */
  close(): Promise<void>;
}

export interface ChallengeSession {
  cookies: Record<string, string>;
  cookieHeader: string;
  userAgent: string;
}

export interface BrowserFetchResult {
  status: number;
  ok: boolean;
  body: string;
  headers: Record<string, string>;
}

export interface ResolvedStream {
  url: string;
  type: string;
  quality: string | null;
  /** Which of several sound tracks this stream carries, when the resolver reported more than one. */
  audioTrack?: string | null;
  headers: Record<string, string>;
  segments: [];
  /** Tracks the resolver reported on the embed page, with the headers to fetch them with. */
  subtitles?: SubtitleTrack[];
}

export interface HarvestedCookie {
  name: string;
  value: string;
  domain?: string;
  path?: string;
  secure?: boolean;
  httpOnly?: boolean;
}

/**
 * Work that needs a real browser engine running a real page: anti-bot challenges, requests that
 * only succeed from inside the page's origin, BROWSER-runtime player resolvers, and sign-in pages.
 * Hidden pages on Electron (BrowserWindow), hidden WebViews on Android - shown to the user when a
 * page needs a person (a captcha, a sign-in form).
 */
export interface BrowserPort {
  /** Loads `url` until it holds every cookie in `cookieNames`, then returns that session. */
  challenge(url: string, cookieNames: string[], forceRefresh: boolean): Promise<ChallengeSession>;
  /** Runs a request from inside a loaded `pageUrl`, with that page's cookies and origin. */
  browserFetch(pageUrl: string, targetUrl: string, options?: { method?: string; headers?: Record<string, string>; body?: string }): Promise<BrowserFetchResult>;
  /** Runs a BROWSER resolver's script inside the embed page and reports the streams it found. */
  resolve(link: PlayerLink, script: string, timeoutMs?: number, parentUrl?: string | null): Promise<ResolvedStream[]>;
  /** Shows `url` to the person until its anti-bot check is passed, and resolves with the session it
   * earned - or null when the window was closed first. */
  solveChallenge(url: string): Promise<ChallengeSession | null>;
  /** Shows a sign-in page and resolves with its cookies once `successCookieName` appears. */
  login(sourceId: string, url: string, successCookieName: string): Promise<HarvestedCookie[]>;
  /** Releases every page held for reuse. */
  dispose(): void;
}

/**
 * Lets the app's own player load streams that require request headers a page may not set
 * (Referer, User-Agent). Electron injects them at the session level, so URLs stay unchanged;
 * Android routes the player through a native proxy, so URLs are rewritten.
 */
export interface PlayerPort {
  /** Resolves a session id; every request the player makes for `url` (and, once registered, other
   * origins of the same stream) carries `headers`. */
  registerHeaders(url: string, headers: Record<string, string> | null | undefined): Promise<string>;
  registerHeaderOrigin(sessionId: string, url: string): boolean;
  unregisterHeaders(sessionId: string): void;
  /** The URL the player should actually load for `url` under `sessionId`. */
  playableUrl(sessionId: string, url: string): string;
  /** Where `url` lands after redirects, or `url` itself when it cannot be checked. Never throws. */
  resolveFinalUrl(url: string, headers?: Record<string, string> | null): Promise<string>;
}

/** Backend -> UI notifications (download progress, sources changed, ...). */
export interface EventsPort {
  emit(channel: string, payload?: unknown): void;
}

export interface AppPort {
  version(): string;
  /** Hands a URL to the system browser. */
  openExternal(url: string): Promise<void>;
  relaunch(): void;
}

export interface Platform {
  kind: PlatformKind;
  capabilities: Capabilities;
  paths: PlatformPaths;
  files: FilesPort;
  secureStore: SecureStorePort;
  http: HttpPort;
  downloads: DownloadTransferPort;
  db: DbPort;
  browser: BrowserPort;
  player: PlayerPort;
  events: EventsPort;
  app: AppPort;
  extensionHost: ExtensionHostPort;
  /** Compiled sources the platform runs itself, beside the JS ones (Android: Aniyomi APK
   * extensions). Absent where there are none. */
  apkSources?: ApkSourcesPort;
  /** Installing an update of the app itself, where the platform does it in-app (Android). */
  appInstaller?: AppInstallerPort;
  /** Reaching a computer on the local network for device sync (Android; the computer listens instead). */
  syncTransport?: SyncTransportPort;
}

export interface SyncTransportPort {
  /** Sends one framed message (core/sync/protocol.ts) to host:port and resolves the answer. */
  request(host: string, port: number, message: string, timeoutMs: number): Promise<string>;
  /** Broadcasts the discovery probe and collects the answers that arrive within the time. */
  discover(timeoutMs: number): Promise<Array<{ deviceId: string; name: string; host: string; port: number; kind: "computer" | "phone" }>>;
  /** What this device is called, as the computer will list it. */
  deviceName(): Promise<string>;
}

export interface AppInstallerPort {
  /** Whether the system lets this app start installing packages. */
  canInstall(): Promise<boolean>;
  /** Takes the person to the system setting that allows it; resolves with the state once they are back. */
  requestPermission(): Promise<boolean>;
  /** Whether the file is a newer build of this very app, signed the same way. `reason` is an
   * UpdateErrorCode (core/updates.ts). */
  verify(path: string, version: string): Promise<{ ok: true } | { ok: false; reason: string }>;
  /** Opens the system installer on the file. Resolves once it is up, not when the install ends. */
  install(path: string): Promise<void>;
}

/**
 * Sources the platform runs natively, addressed by ids starting "apk:". ExtensionRuntime lists them
 * with the JS sources and hands them the same calls it would make of a JS source - method names,
 * arguments and result shapes - so nothing above it can tell the two apart.
 */
export interface ApkSourcesPort {
  list(): Promise<Array<SourceInfo & { packageName: string }>>;
  call(sourceId: string, method: string, args: unknown[]): Promise<unknown>;
  /** Downloads the package's APK and installs it; the signer is trusted on a first install, and an
   * update signed by anyone else is refused. */
  install(url: string, packageName: string): Promise<void>;
  uninstall(packageName: string): Promise<void>;
}

// --- Extension workers -------------------------------------------------------------------------

export type BridgeKind = "netFetch" | "netFetchAll" | "challenge" | "browserFetch";

/** Answers one synchronous host call from a running extension script (fetch, challenge, ...). */
export type BridgeHandler = (kind: BridgeKind, payload: Record<string, unknown>) => Promise<unknown>;

export interface ExtensionWorkerCall {
  sourceId: string;
  method: string;
  args: unknown[];
  /** The directory holding `<sourceId>.js` beside `<sourceId>.manifest.json`. How the code reaches
   * the worker is the port's business: Electron's worker reads (and caches) the file itself,
   * Android's is handed the text. */
  extensionsDir: string;
  /** This source's stored values as of dispatch. */
  storage?: Record<string, string>;
}

export interface ExtensionWorkerResult {
  ok: boolean;
  result?: unknown;
  error?: string;
  /** Storage changes the call made; `null` removes a key. */
  storageWrites?: Record<string, string | null>;
}

/**
 * Runs extension scripts off the UI thread. A script's host calls are synchronous for the script
 * and async for the host: the worker blocks (Atomics.wait on Electron, a held synchronous XHR on
 * Android, where SharedArrayBuffer is unavailable) while `bridge` does the work.
 *
 * `run` resolves with whatever the script answered, failure included. It rejects only when there
 * is no answer: an Error named "TimeoutError" after `timeoutMs`, "AbortError" when `signal` fires,
 * and any other error when the worker itself crashed. A worker that timed out or was aborted is
 * discarded, never reused.
 */
export interface ExtensionHostPort {
  run(call: ExtensionWorkerCall, bridge: BridgeHandler, options: { timeoutMs: number; signal?: AbortSignal }): Promise<ExtensionWorkerResult>;
  /** Starts workers ahead of the first call. */
  warm(): void;
  dispose(): void;
}
