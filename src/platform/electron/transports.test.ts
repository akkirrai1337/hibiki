// The Electron transports against the shared port contracts. Only the adapters that run under
// plain Node are here; safeStorage, BrowserWindow and the Electron-built better-sqlite3 need the
// Electron runtime itself.
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll } from "vitest";
import { describeDownloadTransferContract } from "../contract/downloadTransfer.contract";
import { describeFilesContract } from "../contract/files.contract";
import { describeHttpContract } from "../contract/http.contract";
import { electronDownloadTransfer } from "./downloadTransfer";
import { electronFiles } from "./files";
import { electronHttp } from "./http";

let scratch = "";
beforeAll(async () => {
  scratch = await fs.mkdtemp(path.join(os.tmpdir(), "hibiki-platform-"));
});
afterAll(() => fs.rm(scratch, { recursive: true, force: true }));

describeHttpContract("electron", electronHttp);
describeFilesContract("electron", electronFiles, () => scratch);
describeDownloadTransferContract("electron", electronDownloadTransfer, electronFiles, () => scratch);
