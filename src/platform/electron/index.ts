import { app, shell, type BrowserWindow } from "electron";
import { electronSyncTransport } from "./syncTransport";
import type { Platform } from "../types";
import { electronBrowser } from "./browser";
import { electronDb } from "./db";
import { electronDownloadTransfer } from "./downloadTransfer";
import { ElectronExtensionHost } from "./extensionHost";
import { electronFiles } from "./files";
import { electronHttp } from "./http";
import { electronPaths } from "./paths";
import { electronPlayer } from "./player";
import { electronSecureStore } from "./secureStore";

/** `getWindow` is read at emit time: the window can be recreated (macOS `activate`) while the
 * backend keeps running. */
export function createElectronPlatform(getWindow: () => BrowserWindow | null): Platform {
  return {
    kind: "electron",
    capabilities: {
      windowControls: true,
      zoom: true,
      discordPresence: true,
      hardwareAccelerationToggle: true,
      appUpdates: "installer",
      revealLogFolder: true,
    },
    paths: electronPaths(),
    files: electronFiles,
    secureStore: electronSecureStore,
    http: electronHttp,
    downloads: electronDownloadTransfer,
    db: electronDb,
    browser: electronBrowser,
    player: electronPlayer,
    events: {
      emit(channel, payload) {
        const window = getWindow();
        if (window && !window.isDestroyed()) window.webContents.send(channel, payload);
      },
    },
    app: {
      version: () => app.getVersion(),
      openExternal: (url) => shell.openExternal(url),
      relaunch() {
        app.relaunch();
        app.exit();
      },
    },
    extensionHost: new ElectronExtensionHost(),
    // Reaching another computer for device sync; this one also listens (main/sync.ts).
    syncTransport: electronSyncTransport,
  };
}
