// The Android log sink: the same three files as desktop (core/logFiles.ts) under the app's private
// storage. Lines are gathered and appended in batches - a plugin call per line would cost more than
// the logging is worth - and flushed when the app goes to the background, which is the last moment
// Android reliably gives before it may kill the process.
import { cappedLogSink, LOG_FILE_NAMES, logRotation } from "../../core/logFiles";
import { formatEntry, log, recentEntries, setLogSink } from "../../core/logger";
import { HibikiFiles } from "./native";

const FLUSH_DELAY_MS = 1000;
const FLUSH_SIZE = 64 * 1024;

let logDir: string | null = null;

/** Rotates the previous sessions' files and starts writing this one, beginning with what was
 * logged before the file was ready. Never throws: a log that cannot write is no reason to fail. */
export async function startAndroidLogFile(dataDir: string): Promise<void> {
  const dir = `${dataDir}/logs`;
  try {
    await HibikiFiles.mkdir({ path: dir });
    for (const [from, to] of logRotation((name) => `${dir}/${name}`)) {
      if ((await HibikiFiles.exists({ path: from })).value) await HibikiFiles.rename({ from, to });
    }
    const file = `${dir}/${LOG_FILE_NAMES[0]}`;
    await HibikiFiles.writeText({ path: file, value: "" });
    logDir = dir;

    let pending = "";
    let timer: ReturnType<typeof setTimeout> | null = null;
    // One append at a time, in order: the plugin runs calls on a pool.
    let chain = Promise.resolve();
    const flush = () => {
      if (timer) clearTimeout(timer);
      timer = null;
      if (!pending) return;
      const value = pending;
      pending = "";
      chain = chain.then(() => HibikiFiles.appendText({ path: file, value })).catch(() => {});
    };
    const sink = cappedLogSink((text) => {
      pending += text;
      if (pending.length >= FLUSH_SIZE) flush();
      else if (!timer) timer = setTimeout(flush, FLUSH_DELAY_MS);
    });
    for (const entry of recentEntries()) sink(formatEntry(entry), entry.level);
    setLogSink(sink);
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") flush();
    });
  } catch (error) {
    log("warn", "log", `could not open the log file: ${error instanceof Error ? error.message : String(error)}`);
  }
}

/** The previous session's log, for "export log" after a crash; null when there is none. */
export async function previousSessionLog(): Promise<string | null> {
  if (!logDir) return null;
  try {
    return (await HibikiFiles.readText({ path: `${logDir}/${LOG_FILE_NAMES[1]}` })).value || null;
  } catch {
    return null;
  }
}
