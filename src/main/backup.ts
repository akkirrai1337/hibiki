import fs from "node:fs/promises";
import path from "node:path";
import { app, BrowserWindow, dialog } from "electron";
import { checkpointDb, closeDb, DB_PATH } from "./db";

// Deliberately NOT included: downloaded episode files (ipc/downloads.ts's DOWNLOADS_DIR) - those
// can be many GB, and are re-downloadable from the source at any time, unlike everything below.
const EXTENSIONS_DIR = path.join(app.getPath("userData"), "extensions");

const FORMAT_VERSION = 1;

interface BackupFile {
  formatVersion: number;
  appVersion: string;
  createdAt: number;
  /** hibiki.db, base64 - see checkpointDb() for why a plain file copy is safe to take here. */
  dbBase64: string;
  /** Every file under extensions/ (manifests, scripts, resolvers/ included), relative path -> base64. */
  extensions: Record<string, string>;
  /** The renderer's whole localStorage (zustand's persisted settings stores) - see backup:create's
   * IPC signature, since only the renderer can actually read its own localStorage. */
  localStorage: Record<string, string>;
}

async function listFilesRecursive(dir: string, base = dir): Promise<string[]> {
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const files: string[] = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...(await listFilesRecursive(full, base)));
    else files.push(path.relative(base, full));
  }
  return files;
}

export async function createBackup(localStorageEntries: Record<string, string>): Promise<string | null> {
  const window = BrowserWindow.getFocusedWindow();
  const defaultName = `hibiki-backup-${new Date().toISOString().slice(0, 10)}.hibikibackup`;
  const options: Electron.SaveDialogOptions = {
    title: "Сохранить резервную копию",
    defaultPath: defaultName,
    filters: [{ name: "Резервная копия hibiki", extensions: ["hibikibackup"] }],
  };
  const { canceled, filePath } = await (window ? dialog.showSaveDialog(window, options) : dialog.showSaveDialog(options));
  if (canceled || !filePath) return null;

  checkpointDb();
  const dbBase64 = (await fs.readFile(DB_PATH)).toString("base64");

  const extensionFiles = await listFilesRecursive(EXTENSIONS_DIR);
  const extensions: Record<string, string> = {};
  for (const relativePath of extensionFiles) {
    const contents = await fs.readFile(path.join(EXTENSIONS_DIR, relativePath));
    // Forward slashes regardless of platform, so a backup made on one OS restores correctly on
    // another - `path.join` below rebuilds the real, platform-native separator from these.
    extensions[relativePath.split(path.sep).join("/")] = contents.toString("base64");
  }

  const backup: BackupFile = {
    formatVersion: FORMAT_VERSION,
    appVersion: app.getVersion(),
    createdAt: Date.now(),
    dbBase64,
    extensions,
    localStorage: localStorageEntries,
  };
  await fs.writeFile(filePath, JSON.stringify(backup));
  return filePath;
}

export interface RestoreResult {
  localStorage: Record<string, string>;
}

export async function restoreBackup(): Promise<RestoreResult | null> {
  const window = BrowserWindow.getFocusedWindow();
  const options: Electron.OpenDialogOptions = {
    title: "Выбрать резервную копию",
    properties: ["openFile"],
    filters: [{ name: "Резервная копия hibiki", extensions: ["hibikibackup"] }],
  };
  const { canceled, filePaths } = await (window ? dialog.showOpenDialog(window, options) : dialog.showOpenDialog(options));
  if (canceled || filePaths.length === 0) return null;

  const raw = await fs.readFile(filePaths[0], "utf8");
  let backup: BackupFile;
  try {
    backup = JSON.parse(raw);
  } catch {
    throw new Error("Файл повреждён или не является резервной копией hibiki");
  }
  if (backup.formatVersion !== FORMAT_VERSION) {
    throw new Error("Эта резервная копия сделана в несовместимой версии hibiki");
  }

  // Closed (not just checkpointed) before overwriting - the running process still holds this file
  // open otherwise, and better-sqlite3's own -wal/-shm sidecars from the CURRENT session would
  // otherwise get replayed on top of the restored file's content the next time the app starts,
  // silently reintroducing whatever the restore was meant to undo.
  closeDb();
  await fs.rm(`${DB_PATH}-wal`, { force: true });
  await fs.rm(`${DB_PATH}-shm`, { force: true });
  await fs.writeFile(DB_PATH, Buffer.from(backup.dbBase64, "base64"));

  // Wiped and rebuilt from scratch, not merged - a source uninstalled after this backup was made
  // should actually be gone again post-restore, not linger alongside whatever the backup brings
  // back.
  await fs.rm(EXTENSIONS_DIR, { recursive: true, force: true });
  for (const [relativePath, contentBase64] of Object.entries(backup.extensions)) {
    const destination = path.join(EXTENSIONS_DIR, ...relativePath.split("/"));
    await fs.mkdir(path.dirname(destination), { recursive: true });
    await fs.writeFile(destination, Buffer.from(contentBase64, "base64"));
  }

  return { localStorage: backup.localStorage };
}
