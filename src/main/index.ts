import path from "node:path";
import fs from "node:fs/promises";
import { createReadStream } from "node:fs";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";
import { app, BrowserWindow, ipcMain, Menu, protocol, shell } from "electron";
import { IPC } from "@shared/ipc";
import { ExtensionRuntime } from "../core/extensions/runtime";
import { destroyAllPooledWindows } from "./extensions/browserFetchHost";
import { destroyIdleResolverWindows } from "./extensions/browserResolveHost";
import { destroyAllLoginWindows } from "./extensions/webLogin";
import { registerSourceHandlers } from "./ipc/sources";
import { registerLibraryHandlers } from "./ipc/library";
import { registerTrackingHandlers } from "./ipc/tracking";
import { registerSyncHandlers } from "./ipc/sync";
import { handleTrackingRedirect } from "../core/api/tracking";
import { registerXpEventHandlers } from "./ipc/xpEvents";
import { registerMarketplaceHandlers } from "./ipc/marketplace";
import { repairMissingResolverDependencies } from "../core/api/extensions";
import { registerDownloadHandlers } from "./ipc/downloads";
import { registerProfileBannerHandlers } from "./ipc/profileBanner";
import { installPlayerHeaderInjector, registerPlayerHeaderOrigin, registerPlayerHeaders, unregisterPlayerHeaders } from "./playerHeaders";
import { resolveFinalStreamUrl } from "./playerStream";
import { clearDiscordPresence, setDiscordRpcEnabled, setIdleDiscordPresence, shutdownDiscordRpc, updateDiscordPresence } from "./discordRpc";
import { DEEP_LINK_SCHEME, findDeepLinkInArgv, parseWatchDeepLink } from "../core/deepLink";
import { createBackup, restoreBackup } from "./backup";
import { log, logger, recentEntries, type LogEntry, type LogLevel } from "../core/logger";
import { initLogger } from "./logFile";
import { getPlatform, installPlatform } from "../core/platform";
import { createElectronPlatform } from "../platform/electron";
import { exportLog, openLogFolder } from "./logExport";
import { isHardwareAccelerationEnabled, setHardwareAccelerationEnabled } from "./hardwareAcceleration";
import { collectMemorySnapshot, formatMemorySnapshot } from "./memoryDiagnostics";
import { checkForUpdate, downloadUpdate, installUpdate } from "./appUpdates";
import type { AppUpdate, DiscordPresence, UpdateDownloadProgress } from "@shared/types";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Serves downloaded episode files to the player. A bare `file://` src would work when the
// renderer itself is loaded via `file://` (a packaged build's `loadFile`) but not in dev, where
// the renderer comes from `http://localhost:...` instead - Chromium treats that as cross-origin
// from `file://` and blocks it. A custom scheme works the same way regardless of how the renderer
// itself was loaded, so playback behaves identically in dev and packaged builds. Must be
// registered before the app is ready.
const DOWNLOAD_FILE_SCHEME = "hibiki-download";
// Serves the profile banner (see ipc/profileBanner.ts) the same way - its own scheme rather than
// reusing hibiki-download's, so this one can stay scoped to a folder that has nothing to do with
// downloaded episodes.
const PROFILE_FILE_SCHEME = "hibiki-profile";
protocol.registerSchemesAsPrivileged([
  { scheme: DOWNLOAD_FILE_SCHEME, privileges: { standard: true, secure: true, stream: true, supportFetchAPI: true, corsEnabled: true } },
  { scheme: PROFILE_FILE_SCHEME, privileges: { standard: true, secure: true, stream: true, supportFetchAPI: true, corsEnabled: true } },
]);

// Keep the taskbar identity independent from Electron's development executable name.
app.setName("hibiki");
app.setAppUserModelId("com.hibiki.desktop");

// What lets a "Watch" button on a Discord Rich Presence card (see discordRpc.ts) actually open this
// app - the OS hands a "hibiki://..." link to whichever process registered as its handler, the same
// mechanism Spotify/Steam presence buttons rely on. Safe to call unconditionally; on Windows/Linux
// it's a registry/desktop-file entry, so calling it again on every launch just keeps it current.
app.setAsDefaultProtocolClient(DEEP_LINK_SCHEME);
// macOS delivers a protocol launch through this event instead of a second `second-instance` (below)
// - registered before `whenReady` since a cold start can fire it immediately.
app.on("open-url", (event, url) => {
  event.preventDefault();
  handleDeepLink(url);
});

// A second launch (double-clicking the icon again, a stray shortcut, ...) used to just open a
// second, fully independent window onto the same userData folder - including the same SQLite
// file, which better-sqlite3 has no cross-process locking story for. Two processes writing to it
// at once is exactly how that database ended up corrupted once already. Refusing the second
// launch outright (rather than, say, opening a second window that shares the file some other way)
// keeps there being only ever one process touching it, which is what every other path in this app
// already assumes.
// Has to run before the app is ready. See hardwareAcceleration.ts.
if (!isHardwareAccelerationEnabled()) app.disableHardwareAcceleration();

if (!app.requestSingleInstanceLock()) {
  // `quit()` wouldn't cut it here - it starts an async shutdown sequence, so every side effect
  // below (the `whenReady().then(...)` setup among them) would still run once before it took
  // effect. `exit()` tears the process down immediately instead.
  app.exit(0);
} else {
  // The instance that *did* get the lock hears about every later launch attempt through this -
  // surface the window that's already open rather than leaving the new launch looking like it
  // silently did nothing.
  app.on("second-instance", (_event, argv) => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
    // Windows/Linux deliver a protocol launch (see the "Watch" button in discordRpc.ts) as a second
    // instance with the "hibiki://..." link as a plain argv entry, rather than macOS's "open-url".
    const url = findDeepLinkInArgv(argv);
    if (url) handleDeepLink(url);
  });
}

// Sources are no longer bundled/preloaded — like the Android app, none are installed by default.
// The Sources screen installs extensions here from a repository's marketplace (see
// ipc/marketplace.ts), writing the same <id>.manifest.json + <id>.js pair hibiki-sources itself
// uses, so this must be a writable, per-user location rather than the read-only app bundle.
const EXTENSIONS_DIR = path.join(app.getPath("userData"), "extensions");

const VITE_DEV_SERVER_URL = process.env.VITE_DEV_SERVER_URL;

let mainWindow: BrowserWindow | null = null;
// Set only when a deep link arrives before the renderer is ready to receive it (a cold start, or
// one that raced the window's own first load) - flushed once `did-finish-load` fires below.
let pendingDeepLinkUrl: string | null = null;

// Parses the link and either forwards it to the renderer right away or, if there is nowhere to
// forward it to yet, remembers it for the flush below. Invalid/unrelated links are dropped here -
// nothing downstream ever sees a link this app didn't itself produce.
function handleDeepLink(rawUrl: string): void {
  // AniList coming back from a sign-in started in Settings (see core/tracking): the account is
  // finished in core and announced to the renderer through tracking:changed.
  if (handleTrackingRedirect(rawUrl)) return;
  const target = parseWatchDeepLink(rawUrl);
  if (!target) return;
  if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.webContents.isLoadingMainFrame()) {
    mainWindow.webContents.send(IPC.appDeepLinkWatch, target);
  } else {
    pendingDeepLinkUrl = rawUrl;
  }
}

// Held at module scope only so before-quit can tear its worker pool down - everything else reaches
// it through the IPC handlers it was registered with.
let extensionRuntime: ExtensionRuntime | null = null;

// Windows/Linux: hide the native title bar entirely and draw our own minimize/maximize/close
// buttons in the renderer (see TitleBar.tsx) instead of Electron's titleBarOverlay (Window
// Controls Overlay). titleBarOverlay was tried first and technically worked, but Windows always
// paints a solid, opaque rectangle behind those buttons - there's no way to make that region
// transparent/gradient, which stood out as a visibly different-colored patch once the app grew a
// gradient "background theme" option (see lib/theme.ts) the buttons' backing color couldn't
// follow. Fully custom buttons are just regular page content, so they follow app theming exactly
// the same as everything else, at the cost of us owning their hit-testing/hover/click ourselves
// (see the new window:* IPC handlers below) instead of getting that for free from the OS. macOS
// uses the standard native frame and title bar.
function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 600,
    backgroundColor: "#0c0c0f",
    title: "hibiki",
    icon: path.join(__dirname, "../../electron-builder-resources/hibiki.ico"),
    ...(process.platform === "darwin" ? {} : { titleBarStyle: "hidden" as const }),
    webPreferences: {
      preload: path.join(__dirname, "../preload/index.mjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  // Right-click menu like a browser's: copy for selected text, the editing set in fields. Built from
  // roles, so the labels follow the system language. Nothing to offer -> no menu.
  mainWindow.webContents.on("context-menu", (_e, params) => {
    const items: Electron.MenuItemConstructorOptions[] = [];
    if (params.isEditable) {
      items.push(
        { role: "undo", enabled: params.editFlags.canUndo },
        { role: "redo", enabled: params.editFlags.canRedo },
        { type: "separator" },
        { role: "cut", enabled: params.editFlags.canCut },
        { role: "copy", enabled: params.editFlags.canCopy },
        { role: "paste", enabled: params.editFlags.canPaste },
        { type: "separator" },
        { role: "selectAll" },
      );
    } else if (params.selectionText.trim()) {
      items.push({ role: "copy" }, { type: "separator" }, { role: "selectAll" });
    }
    if (items.length > 0) Menu.buildFromTemplate(items).popup();
  });

  // Keeps the custom maximize/restore button's icon (see TitleBar.tsx) in sync with reality even
  // when the window's maximized state changes from somewhere other than that button itself -
  // double-clicking the title bar, dragging to a screen edge, Win+Up/Down, the taskbar's own
  // right-click menu, ... Registered per-window (not once at module scope) so a window recreated
  // via the `activate` handler below still gets this wired up, not just the very first one.
  mainWindow.on("maximize", () => mainWindow?.webContents.send(IPC.windowMaximizedChanged, true));
  mainWindow.on("unmaximize", () => mainWindow?.webContents.send(IPC.windowMaximizedChanged, false));

  if (VITE_DEV_SERVER_URL) {
    mainWindow.webContents.on("console-message", (_e, level, message, line, sourceId) => {
      console.log(`[renderer:${level}] ${message} (${sourceId}:${line})`);
    });
  }

  // The app menu is disabled (see Menu.setApplicationMenu(null) below), which is what normally
  // wires up DevTools' default accelerators — so re-bind them by hand instead of relying on that.
  mainWindow.webContents.on("before-input-event", (_e, input) => {
    const isToggleCombo =
      input.key === "F12" ||
      ((input.control || input.meta) && input.shift && input.key.toLowerCase() === "i");
    if (isToggleCombo) mainWindow?.webContents.toggleDevTools();

    const isReloadCombo = input.key === "F5" || ((input.control || input.meta) && input.key.toLowerCase() === "r");
    if (isReloadCombo) mainWindow?.webContents.reload();
  });

  if (VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(VITE_DEV_SERVER_URL);
  } else {
    mainWindow.loadFile(path.join(__dirname, "../../dist/index.html"));
  }
}

app.whenReady().then(async () => {
  // First, before any handler can run core code. `mainWindow` is read lazily: it does not exist
  // yet, and the macOS `activate` path can replace it later.
  installPlatform(createElectronPlatform(() => mainWindow));
  initLogger(app.getPath("userData"));
  // Without these, a rejected promise anywhere in main (a download stream, an IPC handler, the
  // Discord socket) vanishes with nothing but a console line no user ever sees - and those are
  // precisely the failures an exported log is meant to explain.
  process.on("uncaughtException", (error) => logger.error("main", `uncaught: ${error.stack ?? error.message}`));
  process.on("unhandledRejection", (reason) => logger.error("main", `unhandled rejection: ${reason instanceof Error ? (reason.stack ?? reason.message) : String(reason)}`));
  Menu.setApplicationMenu(null);

  // `request.url` is `hibiki-download://local/<url-encoded absolute path>` (see
  // downloadFileUrl in the renderer's lib/hibiki.ts) - the host segment is unused, the whole
  // path is carried url-encoded as one opaque data blob rather than as this scheme's own routing,
  // so an absolute Windows path's own "C:" and backslashes round-trip exactly instead of being
  // reinterpreted as host/path segments.
  // The realpath check is what actually matters for safety: without it, a crafted `..` in the
  // encoded path could read any file on disk the app process can see, not just its own downloads.
  //
  // This streams the file (and honors `Range`) by hand with a plain Node read stream, rather than
  // delegating to `net.fetch(pathToFileURL(...))` (the more obvious approach) - that route did
  // forward the Range header through, but <video> still failed to open the file with
  // "FFmpegDemuxer: open context failed" even so (the file itself checks out fine with ffprobe),
  // pointing at some other quirk in how net.fetch's own file:// handling packages up the response
  // for a large file rather than anything about Range specifically. Building the Response
  // ourselves removes that layer entirely instead of chasing exactly what about it was wrong.
  protocol.handle(DOWNLOAD_FILE_SCHEME, async (request) => {
    const url = new URL(request.url);
    const requestedPath = decodeURIComponent(url.pathname.startsWith("/") ? url.pathname.slice(1) : url.pathname);
    try {
      const realPath = await fs.realpath(requestedPath);
      const realDownloadsDir = await fs.realpath(getPlatform().paths.downloads);
      if (!realPath.startsWith(realDownloadsDir + path.sep)) return new Response("Forbidden", { status: 403 });

      const stat = await fs.stat(realPath);
      // A downloaded episode's subtitles live beside it, as WebVTT (see core/api/downloads.ts).
      const lower = realPath.toLowerCase();
      const contentType = lower.endsWith(".mp4") ? "video/mp4" : lower.endsWith(".vtt") ? "text/vtt; charset=utf-8" : "video/mp2t";
      const range = /bytes=(\d+)-(\d*)/.exec(request.headers.get("range") ?? "");

      if (range) {
        const start = Number(range[1]);
        const end = range[2] ? Number(range[2]) : stat.size - 1;
        const stream = Readable.toWeb(createReadStream(realPath, { start, end })) as ReadableStream;
        return new Response(stream, {
          status: 206,
          headers: {
            "Content-Type": contentType,
            "Content-Range": `bytes ${start}-${end}/${stat.size}`,
            "Content-Length": String(end - start + 1),
            "Accept-Ranges": "bytes",
          },
        });
      }

      const stream = Readable.toWeb(createReadStream(realPath)) as ReadableStream;
      return new Response(stream, {
        status: 200,
        headers: { "Content-Type": contentType, "Content-Length": String(stat.size), "Accept-Ranges": "bytes" },
      });
    } catch {
      return new Response("Not found", { status: 404 });
    }
  });

  // `request.url` is `hibiki-profile://local/<filename>` - just a bare filename (see
  // profileBannerUrl in the renderer's lib/hibiki.ts), never a path, so there is no `..` to guard
  // against the way the download scheme above has to.
  const PROFILE_CONTENT_TYPE: Record<string, string> = { gif: "image/gif", png: "image/png", jpg: "image/jpeg", webp: "image/webp", mp4: "video/mp4" };
  protocol.handle(PROFILE_FILE_SCHEME, async (request) => {
    const url = new URL(request.url);
    const filename = decodeURIComponent(url.pathname.startsWith("/") ? url.pathname.slice(1) : url.pathname);
    const extension = filename.split(".").pop() ?? "";
    const contentType = PROFILE_CONTENT_TYPE[extension];
    if (!contentType || filename.includes("/") || filename.includes("\\")) return new Response("Forbidden", { status: 403 });
    try {
      const filePath = path.join(getPlatform().paths.profile, filename);
      const stat = await fs.stat(filePath);
      const range = /bytes=(\d+)-(\d*)/.exec(request.headers.get("range") ?? "");
      if (range) {
        const start = Number(range[1]);
        const end = range[2] ? Number(range[2]) : stat.size - 1;
        const stream = Readable.toWeb(createReadStream(filePath, { start, end })) as ReadableStream;
        return new Response(stream, {
          status: 206,
          headers: { "Content-Type": contentType, "Content-Length": String(end - start + 1), "Content-Range": `bytes ${start}-${end}/${stat.size}`, "Accept-Ranges": "bytes" },
        });
      }
      const stream = Readable.toWeb(createReadStream(filePath)) as ReadableStream;
      return new Response(stream, { status: 200, headers: { "Content-Type": contentType, "Content-Length": String(stat.size), "Accept-Ranges": "bytes" } });
    } catch {
      return new Response("Not found", { status: 404 });
    }
  });

  const runtime = new ExtensionRuntime(EXTENSIONS_DIR);
  // Before any handler exists: a renderer call must never see an empty source list just because
  // the manifests were still being read.
  await runtime.reload();
  extensionRuntime = runtime;

  registerSourceHandlers(runtime);
  registerMarketplaceHandlers(runtime);
  registerLibraryHandlers(runtime);
  registerTrackingHandlers(runtime);
  registerSyncHandlers();
  registerXpEventHandlers();
  // Progress goes out through platform.events, whose window getter is read at send-time: a download
  // can still be running long after the window is recreated (e.g. closed and reopened via the
  // dock/taskbar on macOS).
  registerDownloadHandlers(runtime);
  registerProfileBannerHandlers();
  installPlayerHeaderInjector();
  ipcMain.handle(IPC.playerRegisterHeaders, (_e, url: string, headers: Record<string, string> | null) =>
    registerPlayerHeaders(url, headers),
  );
  ipcMain.handle(IPC.playerRegisterHeaderOrigin, (_e, sessionId: string, url: string) =>
    registerPlayerHeaderOrigin(sessionId, url),
  );
  ipcMain.handle(IPC.playerUnregisterHeaders, (_e, sessionId: string) => unregisterPlayerHeaders(sessionId));
  ipcMain.handle(IPC.playerResolveStreamUrl, (_e, url: string, headers: Record<string, string> | null) =>
    resolveFinalStreamUrl(url, headers),
  );
  ipcMain.handle(IPC.discordSetEnabled, (_e, enabled: boolean) => setDiscordRpcEnabled(enabled));
  ipcMain.handle(IPC.discordUpdatePresence, (_e, presence: DiscordPresence) => updateDiscordPresence(presence));
  ipcMain.handle(IPC.discordSetIdlePresence, () => setIdleDiscordPresence());
  ipcMain.handle(IPC.discordClearPresence, () => clearDiscordPresence());

  // `-webkit-app-region: drag` correctly detects the drag region even while maximized, but the
  // native "unmaximize and follow the cursor" behavior a real OS titlebar gives for free just
  // doesn't happen on Windows for a frameless+titleBarOverlay window - confirmed live, nothing
  // moves at all. Synchronous (sendSync, not invoke) so this runs to completion - unmaximizing the
  // window - before Chromium's own native drag-intent handling for this same mousedown can decide
  // "maximized, nothing to drag" and give up on it.
  ipcMain.on(IPC.windowUnmaximizeForDrag, (event, cursorX: number) => {
    if (mainWindow?.isMaximized()) {
      // Keep the cursor at the same relative X fraction across the titlebar it had before,
      // instead of the window jumping to align its edge under the cursor - matches how a real OS
      // drag-to-restore "peels" the window off from under the pointer.
      const [maximizedWidth] = mainWindow.getSize();
      const fraction = cursorX / maximizedWidth;
      mainWindow.unmaximize();
      const [restoredWidth] = mainWindow.getSize();
      mainWindow.setPosition(Math.round(cursorX - restoredWidth * fraction), 0);
    }
    event.returnValue = true;
  });

  // The custom minimize/maximize/close buttons TitleBar.tsx draws (see the comment on
  // createWindow above for why they're not the OS's own titleBarOverlay ones) - plain
  // fire-and-forget commands, `on`/`send` rather than `handle`/`invoke` since the renderer has
  // nothing to wait on a response for.
  ipcMain.on(IPC.windowMinimize, () => mainWindow?.minimize());
  ipcMain.on(IPC.windowToggleMaximize, () => {
    if (mainWindow?.isMaximized()) mainWindow.unmaximize();
    else mainWindow?.maximize();
  });
  ipcMain.on(IPC.windowClose, () => mainWindow?.close());
  ipcMain.handle(IPC.windowIsMaximized, () => mainWindow?.isMaximized() ?? false);

  ipcMain.handle(IPC.playerCaptureFrame, async () => {
    if (!mainWindow || mainWindow.isDestroyed()) return null;
    // Read decoded video pixels, not the composited window (which includes controls).
    // Execute inside each frame so an embedded player's own video can be inspected too.
    // If a stream forbids canvas export, keep the previous thumbnail rather than
    // falling back to a screenshot containing controls, menus, or advertisements.
    for (const frame of mainWindow.webContents.mainFrame.framesInSubtree) {
      try {
        const dataUrl = await frame.executeJavaScript(`(() => {
          const videos = [...document.querySelectorAll('video')]
            .filter(v => v.readyState >= 2 && v.videoWidth && v.videoHeight && !v.seeking && v.getClientRects().length)
            .sort((a, b) => b.clientWidth * b.clientHeight - a.clientWidth * a.clientHeight);
          for (const video of videos) {
            try {
              const canvas = document.createElement('canvas');
              canvas.width = 400;
              canvas.height = Math.max(1, Math.round(400 * video.videoHeight / video.videoWidth));
              const context = canvas.getContext('2d');
              if (!context) continue;
              context.drawImage(video, 0, 0, canvas.width, canvas.height);
              return canvas.toDataURL('image/jpeg', 0.8);
            } catch { /* Cross-origin or protected video: do not capture the UI. */ }
          }
          return null;
        })()`);
        if (typeof dataUrl === "string" && dataUrl.startsWith("data:image/jpeg;base64,")) return dataUrl;
      } catch { /* A navigating or detached iframe can disappear during capture. */ }
    }
    return null;
  });

  ipcMain.handle(IPC.appGetVersion, () => app.getVersion());
  ipcMain.handle(IPC.updatesCheck, () => checkForUpdate());
  // One handler for both halves on purpose: an installer that has been downloaded but not launched
  // is just a large file in temp, and leaving that state reachable from the renderer invites it.
  ipcMain.handle(IPC.updatesDownloadAndInstall, async (event, update: AppUpdate) => {
    const installerPath = await downloadUpdate(update, (receivedBytes, totalBytes) => {
      if (!event.sender.isDestroyed()) {
        event.sender.send(IPC.updatesProgress, { receivedBytes, totalBytes } satisfies UpdateDownloadProgress);
      }
    });
    await installUpdate(installerPath);
  });
  ipcMain.handle(IPC.updatesOpenRelease, (_e, url: string) => {
    // Only this project's own releases - shell.openExternal hands the string to the OS, so an
    // arbitrary one from the renderer would be a way to launch anything the shell knows how to.
    if (/^https:\/\/github\.com\/akkirrai1337\/hibiki-desktop\/releases\//.test(url)) return shell.openExternal(url);
    logger.warn("update", `refused to open a non-release URL: ${url}`);
    return Promise.resolve();
  });
  ipcMain.handle(IPC.logsExport, () => exportLog());
  ipcMain.handle(IPC.logsRecent, (_e, limit?: number): LogEntry[] => recentEntries(limit ?? 300));
  ipcMain.on(IPC.logsOpenFolder, () => openLogFolder());
  // Per-process memory for Settings > Diagnostics. `record` also writes the snapshot into the log,
  // so an exported log carries the numbers the user was looking at.
  ipcMain.handle(IPC.appGetHardwareAcceleration, () => isHardwareAccelerationEnabled());
  ipcMain.handle(IPC.appSetHardwareAcceleration, (_e, enabled: boolean) => setHardwareAccelerationEnabled(enabled === true));
  ipcMain.handle(IPC.diagnosticsMemory, (_e, record?: boolean) => {
    const snapshot = collectMemorySnapshot();
    if (record) log("info", "memory", formatMemorySnapshot(snapshot));
    return snapshot;
  });
  // The renderer half of the log: playback errors (hls.js, <video> media errors, a failed
  // resolvePlayerLink) only ever surface there, and those are exactly the lines that make an
  // exported log answer "why didn't this episode play" instead of stopping at "the links loaded".
  ipcMain.on(IPC.logsAppend, (_e, level: LogLevel, scope: string, message: string) => {
    log(level, `ui:${scope}`, String(message).slice(0, 4000));
  });
  ipcMain.handle(IPC.backupCreate, (_e, localStorageEntries: Record<string, string>) => createBackup(localStorageEntries));
  ipcMain.handle(IPC.backupRestore, () => restoreBackup());
  // Restoring a backup replaces hibiki.db and the extensions directory out from under this same
  // running process (see backup.ts) - relaunching is simpler and more robust than trying to
  // reopen the DB and re-run ExtensionRuntime.reload() live mid-session, and matches how a restore
  // reads to the user anyway ("this is a fresh start from the backup"), not a hot-reload.
  ipcMain.on(IPC.appRelaunch, () => {
    app.relaunch();
    app.exit();
  });

  createWindow();
  // A transient failure while installing a hidden resolver must not permanently turn a source
  // such as Anichi back into iframe playback. Repair in the background to preserve first paint;
  // installResolver() reloads the runtime, so the next playback request sees the direct extractor.
  void repairMissingResolverDependencies(runtime)
    .then((changed) => {
      if (changed && mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(IPC.sourcesChanged);
    })
    .catch((error) => logger.warn("resolvers", `startup repair failed: ${error instanceof Error ? error.message : String(error)}`));
  // After the window exists, not before: warming the worker pool parses a bundle on new threads,
  // and doing that while Chromium is still bringing up the renderer only slows down first paint.
  // Source queries arriving before warm-up finishes still take the normal fresh-worker path.
  mainWindow?.webContents.once("did-finish-load", () => {
    runtime.warmWorkers();
    // A cold start via a "hibiki://..." link (see handleDeepLink above) has nowhere to send it
    // until the renderer's own listener is up - the window's first `did-finish-load` is that point.
    if (pendingDeepLinkUrl) {
      const url = pendingDeepLinkUrl;
      pendingDeepLinkUrl = null;
      handleDeepLink(url);
    }
  });
  // Windows/Linux launching this app fresh (it was not already running) via a "hibiki://..." link
  // hands it over as a plain argv entry, same shape as the second-instance case above.
  const coldStartUrl = findDeepLinkInArgv(process.argv);
  if (coldStartUrl) handleDeepLink(coldStartUrl);

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("before-quit", () => {
  destroyAllPooledWindows();
  destroyIdleResolverWindows();
  destroyAllLoginWindows();
  // Idle extension worker threads would otherwise keep the process alive past the last window.
  extensionRuntime?.dispose();
  shutdownDiscordRpc();
});
