// The Android Platform: Capacitor plugins for transports and host capabilities, Web Workers for
// extensions. Assembled once at startup by bootstrap.ts.
import type { EventsPort, PlatformPaths, PlayerPort } from "../types";
import type { Platform } from "../types";
import { androidBrowser } from "./browser";
import { createAndroidDb } from "./db";
import { AndroidExtensionHost } from "./extensionHost";
import { HibikiApk, HibikiApp, HibikiFiles, HibikiNet, HibikiSync } from "./native";
import { DISCOVERY_PORT, DISCOVERY_PROBE } from "../../core/sync/protocol";
import { Capacitor } from "@capacitor/core";
import { androidDownloadTransfer, androidFiles, androidHttp, androidSecureStore } from "./transports";

type Listener = (payload: unknown) => void;

/** Backend -> UI notifications stay in this one JS context; the HibikiApi listeners subscribe here. */
export class AndroidEvents implements EventsPort {
  private readonly listeners = new Map<string, Set<Listener>>();

  emit(channel: string, payload?: unknown): void {
    for (const listener of this.listeners.get(channel) ?? []) listener(payload);
  }

  on(channel: string, listener: Listener): () => void {
    const set = this.listeners.get(channel) ?? new Set<Listener>();
    set.add(listener);
    this.listeners.set(channel, set);
    return () => set.delete(listener);
  }
}

// Playback goes through the native stream proxy (StreamProxy.java): every request of a session
// carries its headers - whatever the origin, so registerHeaderOrigin has nothing to add - and the
// proxy follows redirects itself, so there is nothing to pre-resolve either.
function createPlayer(): PlayerPort {
  return {
    registerHeaders: async (_url, headers) => (await HibikiNet.registerStream({ headers: headers ?? {} })).sid,
    registerHeaderOrigin: () => true,
    unregisterHeaders: (sid) => void HibikiNet.unregisterStream({ sid }),
    playableUrl: (sid, url) => `/_hibiki/stream?sid=${encodeURIComponent(sid)}&u=${encodeURIComponent(url)}`,
    resolveFinalUrl: async (url) => url,
  };
}

export async function createAndroidPlatform(version: string): Promise<{ platform: Platform; events: AndroidEvents; migrate: () => Promise<number> }> {
  const { path: userData } = await HibikiFiles.dataDir();
  const join = androidFiles.join;
  const paths: PlatformPaths = {
    userData,
    database: join(userData, "hibiki.db"),
    extensions: join(userData, "extensions"),
    extensionStorage: join(userData, "extension-storage"),
    downloads: join(userData, "downloads"),
    profile: join(userData, "profile"),
  };
  const db = createAndroidDb(paths.database);
  const events = new AndroidEvents();
  const platform: Platform = {
    kind: "android",
    capabilities: {
      windowControls: false,
      zoom: false,
      discordPresence: false,
      hardwareAccelerationToggle: false,
      appUpdates: "apk",
      revealLogFolder: false,
    },
    paths,
    files: androidFiles,
    secureStore: androidSecureStore,
    http: androidHttp,
    downloads: androidDownloadTransfer,
    db,
    browser: androidBrowser,
    player: createPlayer(),
    events,
    app: {
      version: () => version,
      openExternal: async (url) => {
        window.open(url, "_blank");
      },
      relaunch: () => window.location.reload(),
    },
    extensionHost: new AndroidExtensionHost(androidFiles),
    syncTransport: {
      request: async (host, port, message, timeoutMs) => (await HibikiSync.request({ host, port, message, timeoutMs })).message,
      discover: async (timeoutMs) => (await HibikiSync.discover({ timeoutMs, port: DISCOVERY_PORT, probe: DISCOVERY_PROBE })).devices,
      deviceName: async () => (await HibikiSync.deviceName()).name,
    },
    appInstaller: {
      canInstall: async () => (await HibikiApp.canInstallPackages()).granted,
      requestPermission: async () => (await HibikiApp.openInstallSettings()).granted,
      verify: async (path, expectedVersion) => {
        const { ok, reason } = await HibikiApp.verifyPackage({ path, version: expectedVersion });
        return ok ? { ok: true } : { ok: false, reason: reason ?? "unreadable" };
      },
      install: (path) => HibikiApp.installPackage({ path }),
    },
    apkSources: {
      list: async () => {
        const { sources, extensions } = await HibikiApk.list();
        // The icon is the APK's own, saved beside it; the page reaches app files through Capacitor's server.
        const iconOf = new Map(extensions.map((extension) => [extension.packageName, extension.iconPath]));
        return sources.map((source) => {
          const icon = iconOf.get(source.packageName);
          return { ...source, iconUrl: icon ? Capacitor.convertFileSrc(icon) : null };
        });
      },
      install: async (url, packageName) => {
        await HibikiApk.install({ url, packageName });
      },
      uninstall: async (packageName) => {
        await HibikiApk.uninstall({ packageName });
      },
      // The calls ExtensionRuntime makes of a source, answered by HibikiApkPlugin.
      call: async (sourceId, method, args) => {
        switch (method) {
          case "search": return (await HibikiApk.search({ sourceId, request: args[0] ?? {} })).items;
          case "latest": return (await HibikiApk.latest({ sourceId, limit: Number(args[0] ?? 24) })).items;
          case "getById": return HibikiApk.getById({ sourceId, id: String(args[0]) });
          case "getPlaybackGroups": return (await HibikiApk.playbackGroups({ sourceId, titleId: String(args[0]) })).items;
          // (titleId, groupId, episodeId): an Aniyomi episode is found by its own id alone.
          case "getPlayerLinks": return (await HibikiApk.playerLinks({ sourceId, episodeId: String(args[2]) })).items;
          case "getSettings": return HibikiApk.filterCatalog({ sourceId });
          // The source's own settings rows (SourceInfo.settings), read and written in the extension's preferences.
          case "readSettings": return HibikiApk.readSettings({ sourceId });
          case "writeSetting": return HibikiApk.writeSetting({ sourceId, key: String(args[0]), value: args[1] == null ? null : String(args[1]) });
          default: throw new Error(`APK sources do not support ${method}()`);
        }
      },
    },
  };
  return { platform, events, migrate: () => db.migrate() };
}
