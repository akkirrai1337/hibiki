import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import type { MarketplaceExtension } from "@shared/types";
import { createExtensionsApi } from "../../core/api/extensions";
import { createRepositoriesApi } from "../../core/api/repositories";
import { findMissingSources, sourcesInData } from "../../core/api/sourceAutoInstall";
import type { ExtensionRuntime } from "../../core/extensions/runtime";

export function registerMarketplaceHandlers(runtime: ExtensionRuntime): void {
  const { repositories, marketplace } = createRepositoriesApi();
  ipcMain.handle(IPC.sourcesRepositoriesList, () => repositories.list());
  ipcMain.handle(IPC.sourcesRepositoriesAdd, (_e, url: string) => repositories.add(url));
  ipcMain.handle(IPC.sourcesRepositoriesRemove, (_e, url: string) => repositories.remove(url));
  ipcMain.handle(IPC.sourcesMarketplaceFetch, (_e, urls: string[]) => marketplace(urls));

  const extensions = createExtensionsApi(runtime);
  ipcMain.handle(IPC.sourcesInstall, (_e, extension: MarketplaceExtension, originUrl: string) => extensions.install(extension, originUrl));
  ipcMain.handle(IPC.sourcesInstalledVersions, () => extensions.installedVersions());
  ipcMain.handle(IPC.sourcesUninstall, (_e, id: string) => extensions.uninstall(id));
  ipcMain.handle(IPC.sourcesMissing, async () => findMissingSources(runtime, await sourcesInData(), { apkFromDefaultOnly: false }));
}
