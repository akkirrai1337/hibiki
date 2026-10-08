import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import { createProfileApi } from "../../core/api/profile";

export function registerProfileBannerHandlers(): void {
  const profile = createProfileApi();
  ipcMain.handle(IPC.profileSetBanner, (_e, bytes: ArrayBuffer, mimeType: string) => profile.setBanner(bytes, mimeType));
  ipcMain.handle(IPC.profileClearBanner, () => profile.clearBanner());
}
