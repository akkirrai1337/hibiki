// The desktop log sink: core/logger.ts's lines appended to userData/logs/hibiki.log, with the two
// sessions before it kept beside it (see core/logFiles.ts for the names and the size ceiling).
import fs from "node:fs";
import path from "node:path";
import { log, setLogSink } from "../core/logger";
import { cappedLogSink, LOG_FILE_NAMES, logRotation } from "../core/logFiles";

let logFilePath: string | null = null;

/** Called once from main, after `app` is ready (userData isn't resolvable before that). */
export function initLogger(userDataDir: string): void {
  try {
    const dir = path.join(userDataDir, "logs");
    fs.mkdirSync(dir, { recursive: true });
    // Every start begins a file of its own: the session *before* a crash survives the restart that
    // follows it - which is usually the one worth reading.
    for (const [from, to] of logRotation((name) => path.join(dir, name))) {
      if (fs.existsSync(from)) fs.renameSync(from, to);
    }
    // The single file the old scheme rotated into; its content is older than anything kept now.
    fs.rmSync(path.join(dir, "hibiki.previous.log"), { force: true });
    logFilePath = path.join(dir, LOG_FILE_NAMES[0]);
    const writeStream = fs.createWriteStream(logFilePath, { flags: "w" });
    setLogSink(cappedLogSink((text) => writeStream.write(text)));
    log("info", "app", `--- session start (pid ${process.pid}) ---`);
  } catch (error) {
    // A log that can't write itself must never be the reason the app fails to start.
    console.warn("[logger] could not open the log file:", error);
  }
}

export function logFile(): string | null {
  return logFilePath;
}
