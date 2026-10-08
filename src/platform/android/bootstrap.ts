// Brings the backend up inside the Android WebView and exposes it as `window.hibiki` - the same
// object the Electron preload exposes over IPC on desktop, here answered in-process by core.
// Runs before the renderer's own modules load (see src/renderer/android-entry.ts), because
// lib/hibiki.ts reads window.hibiki once at import time.
import type { HibikiApi, PipAction } from "@shared/hibikiApi";
import { IPC } from "@shared/ipc";
import type { AppUpdate, DownloadProgress, TrackerImportProgress, UpdateDownloadProgress } from "@shared/types";
import { createCoreApi } from "../../core/api";
import { installMissingSources } from "../../core/api/sourceAutoInstall";
import { importLegacyData } from "../../core/legacyImport";
import { handleTrackingRedirect } from "../../core/api/tracking";
import { ExtensionRuntime } from "../../core/extensions/runtime";
import { log, recentEntries, renderLog } from "../../core/logger";
import { installPlatform } from "../../core/platform";
import { checkForUpdate, cleanUpdateLeftovers, downloadUpdate, installUpdate } from "../../core/updates";
import { createSyncServerApi } from "../../core/api/sync";
import { deviceId } from "../../core/sync/changes";
import { syncNow } from "../../core/sync/client";
import { DISCOVERY_PORT, DISCOVERY_PROBE, PROTOCOL_VERSION, SYNC_PORT, type DiscoveryAnswer } from "../../core/sync/protocol";
import { handleSyncRequest } from "../../core/sync/server";
import { createAndroidPlatform } from "./index";
import type { Platform } from "../types";
import { previousSessionLog, startAndroidLogFile } from "./logFile";
import { Capacitor, SystemBars, SystemBarsStyle } from "@capacitor/core";
import { HibikiApp, HibikiSync } from "./native";

declare const __APP_VERSION__: string;

/**
 * The "continue watching" frame: the playing video's own decoded pixels drawn to a small canvas,
 * as the desktop main process does, never a screenshot with the controls on it. Streams go
 * through the same-origin /_hibiki/stream route, so the canvas stays readable; a video that
 * still taints it keeps the previous thumbnail.
 */
function captureVideoFrame(): string | null {
  const videos = [...document.querySelectorAll("video")]
    .filter((v) => v.readyState >= 2 && v.videoWidth && v.videoHeight && !v.seeking && v.getClientRects().length)
    .sort((a, b) => b.clientWidth * b.clientHeight - a.clientWidth * a.clientHeight);
  for (const video of videos) {
    try {
      const canvas = document.createElement("canvas");
      canvas.width = 400;
      canvas.height = Math.max(1, Math.round((400 * video.videoHeight) / video.videoWidth));
      const context = canvas.getContext("2d");
      if (!context) continue;
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      return canvas.toDataURL("image/jpeg", 0.8);
    } catch { /* Cross-origin or protected video. */ }
  }
  return null;
}

// Where the one-time carry-over from the Kotlin hibiki is remembered. Written only once it has run
// (or found nothing), so a start that failed half-way tries again next time.
const LEGACY_IMPORTED = "legacy-app-imported";

/**
 * The first start after this app replaced the Kotlin hibiki (the same package): its library and saved
 * positions come over (core/legacyImport.ts) before the first screen is drawn, and the sources they
 * belong to are installed in the background (core/api/sourceAutoInstall.ts), so the titles also open.
 */
async function carryOverLegacyApp(platform: Platform, runtime: ExtensionRuntime): Promise<void> {
  const marker = platform.files.join(platform.paths.userData, LEGACY_IMPORTED);
  if (await platform.files.exists(marker)) return;
  try {
    const data = await HibikiApp.legacyData();
    if (data.libraryError) log("warn", "migration", `previous app's library could not be read: ${data.libraryError}`);
    if (data.found) {
      const result = await importLegacyData(data);
      log("info", "migration", `carried over ${result.library} library titles and ${result.progress} saved positions from the previous app${result.skipped ? `, ${result.skipped} skipped` : ""}`);
      void installMissingSources(runtime, result.sourceIds, "carried-over titles").catch((error) => log("warn", "migration", `sources of the carried-over titles not installed: ${error}`));
    }
    await platform.files.writeText(marker, "1");
  } catch (error) {
    log("warn", "migration", `previous app's data not carried over, will retry next start: ${error}`);
  }
}

export async function installAndroidHibiki(): Promise<void> {
  const version = __APP_VERSION__;
  const { platform, events, migrate } = await createAndroidPlatform(version);
  installPlatform(platform);
  await startAndroidLogFile(platform.paths.userData);
  log("info", "app", `--- session start (android ${version}) ---`);

  const applied = await migrate();
  if (applied > 0) log("info", "db", `applied ${applied} migration(s)`);

  // A package from an update that has since been installed has nothing left to do on disk.
  void cleanUpdateLeftovers().catch((error) => log("warn", "update", `could not clean old update files: ${error}`));

  const runtime = new ExtensionRuntime(platform.paths.extensions);
  await runtime.reload();
  const core = createCoreApi(runtime);
  await carryOverLegacyApp(platform, runtime);
  const nothing = () => () => {};
  const api: HibikiApi = {
    ...core,
    sources: { ...core.sources, onChanged: (callback) => events.on(IPC.sourcesChanged, () => callback()) },
    sync: {
      ...createSyncServerApi(),
      onChanged: (callback) => events.on(IPC.syncChanged, (payload) => callback((payload as { what: "devices" | "data" }).what)),
    },
    tracking: {
      ...core.tracking,
      onChanged: (callback) => events.on(IPC.trackingChanged, () => callback()),
      onImportProgress: (callback) => events.on(IPC.trackingImportProgress, (progress) => callback(progress as TrackerImportProgress)),
    },
    downloads: {
      ...core.downloads,
      onProgress: (callback) => events.on(IPC.downloadsProgress, (progress) => callback(progress as DownloadProgress)),
      // Capacitor's local server hands out files from the app's storage, with Range for seeking.
      fileUrl: (filePath) => Capacitor.convertFileSrc(filePath),
    },
    player: {
      registerHeaders: (url, headers) => platform.player.registerHeaders(url, headers),
      registerHeaderOrigin: async (sessionId, url) => platform.player.registerHeaderOrigin(sessionId, url),
      unregisterHeaders: async (sessionId) => platform.player.unregisterHeaders(sessionId),
      resolveStreamUrl: (url, headers) => platform.player.resolveFinalUrl(url, headers),
      captureFrame: async () => captureVideoFrame(),
      streamUrl: (sessionId, url) => platform.player.playableUrl(sessionId, url),
    },
    // Desktop-only surfaces: present so the renderer's calls are harmless, hidden from the UI by
    // platform.capabilities as the mobile layout lands.
    discord: { setEnabled: async () => {}, updatePresence: async () => {}, setIdlePresence: async () => {}, clearPresence: async () => {} },
    window: { unmaximizeForDrag() {}, minimize() {}, toggleMaximize() {}, close() {}, isMaximized: async () => false, onMaximizedChanged: nothing },
    updates: {
      check: () => checkForUpdate("android"),
      downloadAndInstall: async (update: AppUpdate) => {
        const path = await downloadUpdate(update, (receivedBytes, totalBytes) => {
          events.emit(IPC.updatesProgress, { receivedBytes, totalBytes } satisfies UpdateDownloadProgress);
        });
        await installUpdate(path, update.version);
      },
      openRelease: (url) => platform.app.openExternal(url),
      onProgress: (callback) => events.on(IPC.updatesProgress, (progress) => callback(progress as UpdateDownloadProgress)),
      installPermission: {
        granted: () => platform.appInstaller!.canInstall(),
        request: () => platform.appInstaller!.requestPermission(),
      },
    },
    zoom: { set() {}, get: () => 1 },
    profile: {
      ...core.profile,
      // Capacitor's own local server hands out files from the app's storage under this URL. Each
      // banner has a name of its own (see core/api/profile.ts), so no cache-busting is needed.
      bannerUrl: (filename) => Capacitor.convertFileSrc(platform.files.join(platform.paths.profile, filename)),
    },
    platform: "android",
    device: {
      onBack: (callback) => {
        window.addEventListener("hibikiback", callback);
        return () => window.removeEventListener("hibikiback", callback);
      },
      minimize: () => void HibikiApp.minimize(),
      setSystemBars: async ({ hidden, style }) => {
        // "dark" bars carry light icons - the app's dark theme; "light" bars carry dark icons.
        if (style) await SystemBars.setStyle({ style: style === "dark" ? SystemBarsStyle.Dark : SystemBarsStyle.Light });
        // Hidden is the player's immersive full screen, which HibikiApp keeps applied across rotations.
        if (hidden !== undefined) await HibikiApp.setImmersive({ value: hidden });
      },
      keepAwake: (on) => HibikiApp.keepAwake({ value: on }),
      setOrientation: (orientation) => HibikiApp.setOrientation({ value: orientation }),
      setBackgroundWork: async (work) => {
        await HibikiApp.setBackgroundWork(work ? { active: true, ...work } : { active: false }).catch((error) => {
          log("warn", "background", `background work not started: ${error instanceof Error ? error.message : String(error)}`);
        });
      },
      pip: {
        update: (state) => void HibikiApp.updatePip({ ...state, width: state.width ? Math.round(state.width) : undefined, height: state.height ? Math.round(state.height) : undefined }).catch(() => undefined),
        enter: async () => (await HibikiApp.enterPip().catch(() => ({ entered: false }))).entered,
        onAction: (callback) => {
          const handle = HibikiApp.addListener("pipAction", (event) => callback(event.action as PipAction));
          return () => void handle.then((h) => h.remove());
        },
        onModeChange: (callback) => {
          const listener = (event: Event) => callback(Boolean((event as Event & { active?: boolean }).active));
          window.addEventListener("hibikipip", listener);
          return () => window.removeEventListener("hibikipip", listener);
        },
      },
    },
    app: {
      getVersion: async () => version,
      relaunch: () => platform.app.relaunch(),
      getHardwareAcceleration: async () => true,
      setHardwareAcceleration: async () => {},
      onDeepLinkWatch: nothing,
    },
    logs: {
      // No save dialog on the phone: the share sheet is where the file goes (a messenger, Files...).
      // Resolves null like a cancelled desktop dialog - there is no saved path to report.
      export: async () => {
        const sources = await core.sources.list().then((list) => list.map((source) => source.id).join(", ")).catch(() => "?");
        const current = renderLog({
          app: `hibiki ${version} (android)`,
          webview: navigator.userAgent,
          exportedAt: new Date().toISOString(),
          sources: sources || "none",
        });
        // The session before this one too: a report is often sent right after a crash, when this
        // session holds nothing about what happened.
        const previous = await previousSessionLog();
        const text = previous ? `${current}\n# --- previous session ---\n\n${previous}` : current;
        // Local time, as the user will look for it.
        const now = new Date();
        const stamp = new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 19).replace(/[T:]/g, "-");
        await HibikiApp.shareText({ name: `hibiki-log-${stamp}.txt`, text });
        return null;
      },
      recent: async (limit) => recentEntries(limit ?? 300),
      openFolder() {},
      memory: async () => ({ time: Date.now(), processes: [], totalMb: 0, mainHeapUsedMb: 0, rendererCount: 0 }),
      append: (level, scope, message) => log(level, `ui:${scope}`, String(message).slice(0, 4000)),
    },
    backup: { create: async () => null, restore: async () => null },
  };

  window.hibiki = api;
  // The player locks the screen to landscape, hides the system bars and keeps the screen on, all in
  // the activity - which outlives this page. A page that restarts while the player was open (the
  // app sent to the background for long, then reloaded) never runs the player's cleanup, so start
  // from the defaults rather than from whatever was left.
  void Promise.all([
    HibikiApp.setOrientation({ value: "auto" }),
    HibikiApp.setImmersive({ value: false }),
    HibikiApp.keepAwake({ value: false }),
  ]).catch((error) => log("warn", "app", `screen state not reset: ${error instanceof Error ? error.message : String(error)}`));
  runtime.warmWorkers();
  // AniList's sign-in comes back as hibiki://anilist-auth: to a running app as an event, or as the
  // link the app was (re)started with when Android had ended it while the browser was in front.
  void HibikiApp.addListener("deepLink", (event) => void handleTrackingRedirect(event.url));
  void HibikiApp.takeLaunchUrl().then(({ url }) => url && handleTrackingRedirect(url)).catch(() => undefined);

  // Device sync with the paired computer (core/sync): soon after start, on coming back to the
  // screen, on leaving it (a moment later, so the progress saved on the way out goes too - Android
  // may freeze the app soon after), and every few minutes while it is open. Without a paired
  // computer each of these is a single empty read.
  const syncQuietly = () => void syncNow().catch(() => undefined);
  window.setTimeout(syncQuietly, 3_000);
  document.addEventListener("visibilitychange", () => {
    window.setTimeout(syncQuietly, document.visibilityState === "visible" ? 500 : 1_500);
  });

  // While on screen this phone also waits for other devices, as a computer does all the time - so
  // another phone can pair with it (moving to a new phone) and sync while both are open.
  const phoneName = (await HibikiSync.deviceName().catch(() => ({ name: "Android" }))).name;
  void HibikiSync.addListener("request", (event) => {
    void handleSyncRequest(event.message, event.address, phoneName)
      .then((message) => HibikiSync.respond({ id: event.id, message }))
      .catch((error) => log("warn", "sync", `request from ${event.address} not answered: ${error instanceof Error ? error.message : String(error)}`));
  });
  // The socket side's own troubles (a timed-out connection, discovery not started), which happen in
  // Java and would otherwise be seen only in logcat.
  void HibikiSync.addListener("log", (event) => log(event.level, "sync", event.message));
  const listen = async () => {
    const answer: DiscoveryAnswer = { app: "hibiki", v: PROTOCOL_VERSION, deviceId: await deviceId(), name: phoneName, port: SYNC_PORT, kind: "phone" };
    await HibikiSync.startServer({ port: SYNC_PORT, discoveryPort: DISCOVERY_PORT, probe: DISCOVERY_PROBE, answer: JSON.stringify(answer) })
      .then(() => log("debug", "sync", `waiting for other devices on port ${SYNC_PORT} as "${phoneName}"`))
      .catch((error) => log("warn", "sync", `not waiting for other devices: ${error instanceof Error ? error.message : String(error)}`));
  };
  if (document.visibilityState === "visible") void listen();
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void listen();
    else {
      log("debug", "sync", "in the background; no longer waiting for other devices");
      void HibikiSync.stopServer().catch(() => undefined);
    }
  });
  window.setInterval(() => {
    if (document.visibilityState === "visible") syncQuietly();
  }, SYNC_INTERVAL_MS);
}

const SYNC_INTERVAL_MS = 3 * 60 * 1000;
