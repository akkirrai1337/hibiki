import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import type { DownloadRequest } from "@shared/types";
import { createDownloadsApi } from "../../core/api/downloads";
import type { ExtensionRuntime } from "../../core/extensions/runtime";

export function registerDownloadHandlers(runtime: ExtensionRuntime): void {
  const downloads = createDownloadsApi(runtime);
  ipcMain.handle(IPC.downloadsStart, (_e, request: DownloadRequest) => downloads.start(request));
  ipcMain.handle(IPC.downloadsPause, (_e, episodeId: string) => downloads.pause(episodeId));
  ipcMain.handle(IPC.downloadsResume, (_e, episodeId: string) => downloads.resume(episodeId));
  ipcMain.handle(IPC.downloadsCancel, (_e, episodeId: string) => downloads.cancel(episodeId));
  ipcMain.handle(IPC.downloadsList, () => downloads.list());
  ipcMain.handle(IPC.downloadsGetForEpisode, (_e, sourceId: string, animeId: string, episodeId: string) =>
    downloads.getForEpisode(sourceId, animeId, episodeId),
  );
  ipcMain.handle(IPC.downloadsRemove, (_e, sourceId: string, animeId: string, episodeId: string) => downloads.remove(sourceId, animeId, episodeId));
}
