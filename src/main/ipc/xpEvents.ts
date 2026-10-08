import { ipcMain } from "electron";
import { IPC } from "@shared/ipc";
import { createXpApi } from "../../core/api/xp";

export function registerXpEventHandlers(): void {
  const xp = createXpApi();
  ipcMain.handle(IPC.xpEventsList, () => xp.list());
  ipcMain.handle(IPC.xpEventsRecord, (_e, kind: string, value: number, createdAt: number) => xp.record(kind, value, createdAt));
  ipcMain.handle(IPC.xpEventsClear, () => xp.clear());
}
