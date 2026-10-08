import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import type { LibraryEntry, WatchProgress } from "@shared/types";
import { createLibraryApi } from "../../core/api/library";
import type { ExtensionRuntime } from "../../core/extensions/runtime";

export function registerLibraryHandlers(runtime: ExtensionRuntime): void {
  const { ratings, library, progress } = createLibraryApi(runtime);

  ipcMain.handle(IPC.ratingGet, (_e, sourceId: string, animeId: string) => ratings.get(sourceId, animeId));
  ipcMain.handle(IPC.ratingSet, (_e, sourceId: string, animeId: string, rating: number | null) => ratings.set(sourceId, animeId, rating));

  ipcMain.handle(IPC.libraryList, () => library.list());
  ipcMain.handle(IPC.libraryUpsert, (_e, entry: LibraryEntry) => library.upsert(entry));
  ipcMain.handle(IPC.libraryRemove, (_e, sourceId: string, animeId: string) => library.remove(sourceId, animeId));

  ipcMain.handle(IPC.progressGet, (_e, sourceId: string, titleId: string, episodeId: string) => progress.get(sourceId, titleId, episodeId));
  ipcMain.handle(IPC.progressUpsert, (_e, incoming: WatchProgress) => progress.upsert(incoming));
  ipcMain.handle(IPC.progressListRecent, (_e, limit: number) => progress.listRecent(limit));
  ipcMain.handle(IPC.progressListForAnime, (_e, sourceId: string, titleId: string) => progress.listForAnime(sourceId, titleId));
  ipcMain.handle(IPC.progressRemoveForAnime, (_e, sourceId: string, titleId: string) => progress.removeForAnime(sourceId, titleId));
  ipcMain.handle(IPC.progressRemoveEpisode, (_e, sourceId: string, titleId: string, episodeId: string) =>
    progress.removeEpisode(sourceId, titleId, episodeId),
  );
  ipcMain.handle(IPC.progressSaveThumbnail, (_e, sourceId: string, titleId: string, episodeId: string, dataUrl: string) =>
    progress.saveThumbnail(sourceId, titleId, episodeId, dataUrl),
  );
  ipcMain.handle(IPC.progressListDailyActivity, (_e, days: number) => progress.listDailyActivity(days));
}
