// "Export log" from Settings → Data: writes the whole in-memory session log (see logger.ts) to a
// plain .log file the user picks, with a short environment header on top so a report doesn't have
// to be followed up with "which version / which OS / which sources".
import fs from "node:fs/promises";
import path from "node:path";
import { app, BrowserWindow, dialog, shell } from "electron";
import { logger, renderLog } from "../core/logger";
import { logFile } from "./logFile";

async function installedSourceSummary(): Promise<string> {
  const dir = path.join(app.getPath("userData"), "extensions");
  try {
    const files = await fs.readdir(dir);
    const ids = files.filter((f) => f.endsWith(".manifest.json")).map((f) => f.replace(".manifest.json", ""));
    const resolverDir = path.join(dir, "resolvers");
    let resolvers: string[] = [];
    try {
      resolvers = (await fs.readdir(resolverDir))
        .filter((f) => f.endsWith(".manifest.json"))
        .map((f) => f.replace(".manifest.json", ""));
    } catch { /* No resolvers installed yet. */ }
    return `${ids.join(", ") || "none"} | resolvers: ${resolvers.join(", ") || "none"}`;
  } catch {
    return "unavailable";
  }
}

/** Resolves the written path, or null if the user cancelled the save dialog. */
export async function exportLog(): Promise<string | null> {
  const window = BrowserWindow.getFocusedWindow();
  const defaultName = `hibiki-log-${new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19)}.log`;
  const options: Electron.SaveDialogOptions = {
    title: "Сохранить журнал",
    defaultPath: defaultName,
    filters: [{ name: "Журнал", extensions: ["log", "txt"] }],
  };
  const { canceled, filePath } = await (window ? dialog.showSaveDialog(window, options) : dialog.showSaveDialog(options));
  if (canceled || !filePath) return null;

  const contents = renderLog({
    app: `hibiki ${app.getVersion()}`,
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
    platform: `${process.platform} ${process.arch} (${process.getSystemVersion?.() ?? "?"})`,
    exportedAt: new Date().toISOString(),
    sources: await installedSourceSummary(),
  });
  await fs.writeFile(filePath, contents, "utf-8");
  logger.info("app", `log exported to ${filePath}`);
  return filePath;
}

/** Reveals the rotating on-disk log (logger.ts) in the OS file manager - the fallback for a
 * session that died before anyone could press Export. */
export function openLogFolder(): void {
  const file = logFile();
  if (file) shell.showItemInFolder(file);
  else shell.openPath(path.join(app.getPath("userData"), "logs"));
}
