import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import type { TrackerId } from "@shared/types";
import { createTrackingApi } from "../../core/api/tracking";
import type { ExtensionRuntime } from "../../core/extensions/runtime";

export function registerTrackingHandlers(runtime: ExtensionRuntime): void {
  const tracking = createTrackingApi(runtime);
  ipcMain.handle(IPC.trackingAccount, (_e, tracker: TrackerId) => tracking.account(tracker));
  ipcMain.handle(IPC.trackingSignIn, (_e, tracker: TrackerId) => tracking.signIn(tracker));
  ipcMain.handle(IPC.trackingSignOut, (_e, tracker: TrackerId) => tracking.signOut(tracker));
  ipcMain.handle(IPC.trackingGetLink, (_e, tracker: TrackerId, sourceId: string, animeId: string) => tracking.getLink(tracker, sourceId, animeId));
  ipcMain.handle(IPC.trackingSearch, (_e, tracker: TrackerId, query: string) => tracking.search(tracker, query));
  ipcMain.handle(IPC.trackingSetLink, (_e, tracker: TrackerId, sourceId: string, animeId: string, mediaId: number | null) =>
    tracking.setLink(tracker, sourceId, animeId, mediaId),
  );
  ipcMain.handle(IPC.trackingRemoveFromList, (_e, tracker: TrackerId, sourceId: string, animeId: string) => tracking.removeFromList(tracker, sourceId, animeId));
  ipcMain.handle(IPC.trackingSetFavourite, (_e, tracker: TrackerId, sourceId: string, animeId: string, favourite: boolean) =>
    tracking.setFavourite(tracker, sourceId, animeId, favourite),
  );
  ipcMain.handle(IPC.trackingImport, (_e, tracker: TrackerId, sourceId: string) => tracking.importLibrary(tracker, sourceId));
}
