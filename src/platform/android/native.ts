// The Java plugins under android/app/src/main/java/app/hibiki, as the Android adapters call them.
import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";
import type { SourceInfo } from "@shared/types";

export interface HibikiNetPlugin {
  request(options: {
    id?: string;
    url: string;
    method?: string;
    headers?: Record<string, string>;
    body?: string;
    followRedirects?: boolean;
    /** 0 = no overall deadline. */
    timeoutMs?: number;
  }): Promise<{ status: number; url: string; headers: Record<string, string[]>; body: string }>;
  cancel(options: { id: string }): Promise<void>;
  registerStream(options: { headers: Record<string, string> }): Promise<{ sid: string }>;
  unregisterStream(options: { sid: string }): Promise<void>;
  bridgeResolve(options: { id: string; body: string }): Promise<void>;
}

export interface HibikiFilesPlugin {
  dataDir(): Promise<{ path: string }>;
  exists(options: { path: string }): Promise<{ value: boolean }>;
  stat(options: { path: string }): Promise<{ size?: number; mtimeMs?: number; isDirectory?: boolean; missing?: boolean }>;
  list(options: { path: string }): Promise<{ entries: string[] }>;
  readText(options: { path: string }): Promise<{ value: string }>;
  writeText(options: { path: string; value: string }): Promise<void>;
  appendText(options: { path: string; value: string }): Promise<void>;
  readBytes(options: { path: string }): Promise<{ base64: string }>;
  writeBytes(options: { path: string; base64: string }): Promise<void>;
  mkdir(options: { path: string }): Promise<void>;
  remove(options: { path: string }): Promise<void>;
  rename(options: { from: string; to: string }): Promise<void>;
}

export interface HibikiDbPlugin {
  open(options: { path: string }): Promise<void>;
  close(): Promise<void>;
  exec(options: { sql: string }): Promise<void>;
  query(options: { sql: string; params: unknown[]; method: "run" | "all" | "values" | "get" }): Promise<{ rows: unknown[][] }>;
}

export interface HibikiSecurePlugin {
  isAvailable(): Promise<{ value: boolean }>;
  encrypt(options: { value: string }): Promise<{ value: string }>;
  decrypt(options: { value: string }): Promise<{ value: string }>;
}

export interface HibikiDownloadsPlugin {
  open(options: { id: string; url: string; headers?: Record<string, string> }): Promise<{ status: number; headers: Record<string, string[]>; contentLength: number }>;
  pipe(options: { id: string; filePath: string; append: boolean; buffered: boolean }): Promise<{ bytesWritten: number }>;
  cancel(options: { id: string }): Promise<void>;
  addListener(event: "progress", listener: (event: { id: string; received: number }) => void): Promise<PluginListenerHandle>;
}

export interface HibikiBrowserPlugin {
  open(options: { key: string; url: string; headers?: Record<string, string>; clearOrigin?: boolean }): Promise<void>;
  eval(options: { key: string; js: string }): Promise<{ value: string }>;
  show(options: { key: string }): Promise<void>;
  cookies(options: { url: string }): Promise<{ value: string }>;
  userAgent(): Promise<{ value: string }>;
  close(options: { key: string }): Promise<void>;
  /** A shown page the user dismissed with Back. */
  addListener(event: "closed", listener: (event: { key: string }) => void): Promise<PluginListenerHandle>;
}

export interface HibikiResolverPlugin {
  open(options: { key: string; bootScript: string; resolverScript: string }): Promise<void>;
  setResolverScript(options: { key: string; resolverScript: string }): Promise<void>;
  navigate(options: { key: string; url: string; headers?: Record<string, string>; documentOnly?: boolean }): Promise<void>;
  setDocumentOnly(options: { key: string; value: boolean }): Promise<void>;
  evalTop(options: { key: string; js: string }): Promise<{ value: string; url?: string }>;
  frames(options: { key: string }): Promise<{ frames: Array<{ id: number; url: string; origin: string; main: boolean }> }>;
  frameCall(options: { key: string; frameId: number; id: string; payload: string }): Promise<{ raw: string }>;
  takeCaptures(options: { key: string }): Promise<{ captures: Array<{ url: string; headers: Record<string, string> }> }>;
  userAgent(): Promise<{ value: string }>;
  cookies(options: { url: string }): Promise<{ value: string }>;
  close(options: { key: string }): Promise<void>;
}

export interface HibikiAppPlugin {
  /** The Kotlin hibiki's library rows and saved positions, if it was installed before this app replaced it. */
  legacyData(): Promise<{
    found: boolean;
    library: Array<{ titleId: string; animeJson?: string; categories: string; addedAt?: number }>;
    progress: Record<string, string>;
    libraryError?: string;
  }>;
  minimize(): Promise<void>;
  keepAwake(options: { value: boolean }): Promise<void>;
  setImmersive(options: { value: boolean }): Promise<void>;
  setOrientation(options: { value: "landscape" | "portrait" | "sensor" | "auto" }): Promise<void>;
  /** The system share sheet with a text file of that name. */
  shareText(options: { name: string; text: string }): Promise<void>;
  updatePip(options: { enabled: boolean; playing: boolean; hasPrevious: boolean; hasNext: boolean; width?: number; height?: number; labels: Record<string, string> }): Promise<void>;
  enterPip(): Promise<{ entered: boolean }>;
  addListener(event: "pipAction", listener: (event: { action: string }) => void): Promise<PluginListenerHandle>;
  /** A hibiki:// link opened while the app runs (AniList's sign-in redirect). */
  addListener(event: "deepLink", listener: (event: { url: string }) => void): Promise<PluginListenerHandle>;
  /** The hibiki:// link the app was started with, once. */
  takeLaunchUrl(): Promise<{ url: string | null }>;
  /** Starts, updates or (inactive) stops the foreground service that keeps downloads running in the background. */
  setBackgroundWork(options: { active: boolean; title?: string; text?: string; progress?: number }): Promise<void>;
  /** Whether the system lets the app start installing packages (always true before Android 8). */
  canInstallPackages(): Promise<{ granted: boolean }>;
  /** The "install unknown apps" screen for this app; answers with the setting once the person is back. */
  openInstallSettings(): Promise<{ granted: boolean }>;
  /** `reason` is an UpdateErrorCode (core/updates.ts). */
  verifyPackage(options: { path: string; version: string }): Promise<{ ok: boolean; reason?: string }>;
  /** Opens the system installer; answers once it is up. */
  installPackage(options: { path: string }): Promise<void>;
}

/** An APK source extension installed inside the app (see android/.../apk/HibikiApkPlugin.kt). */
export interface ApkExtensionEntry {
  packageName: string;
  name: string;
  versionName: string;
  versionCode: number;
  isNsfw: boolean;
  trusted: boolean;
  fingerprint: string | null;
  iconPath: string | null;
  sourceIds: string[];
  error: string | null;
}

export interface HibikiApkPlugin {
  list(): Promise<{ sources: Array<SourceInfo & { packageName: string }>; extensions: ApkExtensionEntry[] }>;
  refresh(): Promise<{ sources: SourceInfo[]; extensions: ApkExtensionEntry[] }>;
  install(options: { url: string; packageName?: string }): Promise<{ packageName: string; name: string; versionName: string; fingerprint: string; trusted: boolean }>;
  trust(options: { packageName: string; fingerprint: string }): Promise<{ sources: SourceInfo[]; extensions: ApkExtensionEntry[] }>;
  uninstall(options: { packageName: string }): Promise<{ sources: SourceInfo[]; extensions: ApkExtensionEntry[] }>;
  search(options: { sourceId: string; request: unknown }): Promise<{ items: unknown[] }>;
  latest(options: { sourceId: string; limit: number }): Promise<{ items: unknown[] }>;
  getById(options: { sourceId: string; id: string }): Promise<unknown>;
  playbackGroups(options: { sourceId: string; titleId: string }): Promise<{ items: unknown[] }>;
  playerLinks(options: { sourceId: string; episodeId: string }): Promise<{ items: unknown[] }>;
  filterCatalog(options: { sourceId: string }): Promise<unknown>;
  readSettings(options: { sourceId: string }): Promise<Record<string, string>>;
  writeSetting(options: { sourceId: string; key: string; value: string | null }): Promise<void>;
}

/** Device sync's transport (android/.../HibikiSyncPlugin.java). */
export interface HibikiSyncPlugin {
  request(options: { host: string; port: number; message: string; timeoutMs: number }): Promise<{ message: string }>;
  discover(options: { timeoutMs: number; port: number; probe: string }): Promise<{ devices: Array<{ deviceId: string; name: string; host: string; port: number; kind: "computer" | "phone" }> }>;
  deviceName(): Promise<{ name: string }>;
  /** Listens for other devices while the app is on screen; each request arrives as a "request" event. */
  startServer(options: { port: number; discoveryPort: number; probe: string; answer: string }): Promise<void>;
  stopServer(): Promise<void>;
  /** The answer to a "request" event. */
  respond(options: { id: string; message: string }): Promise<void>;
  addListener(event: "request", listener: (event: { id: string; message: string; address: string }) => void): Promise<PluginListenerHandle>;
  /** Trouble on the socket side, for the app log. */
  addListener(event: "log", listener: (event: { level: "debug" | "info" | "warn" | "error"; message: string }) => void): Promise<PluginListenerHandle>;
}

export const HibikiApp = registerPlugin<HibikiAppPlugin>("HibikiApp");
export const HibikiSync = registerPlugin<HibikiSyncPlugin>("HibikiSync");
export const HibikiApk = registerPlugin<HibikiApkPlugin>("HibikiApk");
export const HibikiNet = registerPlugin<HibikiNetPlugin>("HibikiNet");
export const HibikiResolver = registerPlugin<HibikiResolverPlugin>("HibikiResolver");
export const HibikiFiles = registerPlugin<HibikiFilesPlugin>("HibikiFiles");
export const HibikiDb = registerPlugin<HibikiDbPlugin>("HibikiDb");
export const HibikiSecure = registerPlugin<HibikiSecurePlugin>("HibikiSecure");
export const HibikiDownloads = registerPlugin<HibikiDownloadsPlugin>("HibikiDownloads");
export const HibikiBrowser = registerPlugin<HibikiBrowserPlugin>("HibikiBrowser");

/** An Error named like the Fetch API's, so shared code tells failures apart the same way on every platform. */
export function namedError(name: "AbortError" | "TimeoutError", message: string): Error {
  return Object.assign(new Error(message), { name });
}

/** Capacitor rejects with an object carrying the plugin's `code`. */
export function nativeCode(error: unknown): string | undefined {
  return (error as { code?: string } | null)?.code;
}

export function nativeMessage(error: unknown): string {
  return error instanceof Error ? error.message : String((error as { message?: string })?.message ?? error);
}
