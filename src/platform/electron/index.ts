import path from "node:path";
import { app, shell, type BrowserWindow } from "electron";
import { ApkHostProcess, apkSourcesPort, locateApkHost, setApkHost, systemLocateOptions } from "./apkHost";
import { desktopUpdateKey } from "@shared/appUpdateSelection";
import { electronSyncTransport } from "./syncTransport";
import type { Platform } from "../types";
import { electronAppInstaller } from "./appInstaller";
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
  // APK sources run in a JVM process of their own (see apkHost.ts); an install without the jars or
  // a Java to run them simply has none, and the rest of the app does not know the difference.
  const apkLocation = locateApkHost(systemLocateOptions(app.getAppPath(), app.isPackaged ? process.resourcesPath : null));
  const apkHost = apkLocation ? new ApkHostProcess(apkLocation, path.join(app.getPath("userData"), "apk-host")) : null;
  setApkHost(apkHost);
  return {
    kind: "electron",
    capabilities: {
      windowControls: true,
      zoom: true,
      discordPresence: true,
      hardwareAccelerationToggle: true,
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
      updateKey: () => desktopUpdateKey(process.platform, process.arch),
      openExternal: (url) => shell.openExternal(url),
      relaunch() {
        app.relaunch();
        app.exit();
      },
    },
    extensionHost: new ElectronExtensionHost(),
    appInstaller: electronAppInstaller,
    // Reaching another computer for device sync; this one also listens (main/sync.ts).
    syncTransport: electronSyncTransport,
    apkSources: apkHost ? apkSourcesPort(apkHost) : undefined,
  };
}
