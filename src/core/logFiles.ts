// The on-disk log, the same on every platform: the running session's file plus the two before it,
// each with a ceiling. A session past the soft limit keeps only warnings and errors; past the hard
// limit it stops writing. So the log can never grow without bound - not even from a runaway loop -
// while the start of the session (what led up to it) and its failures (what went wrong) survive.
import type { LogLevel } from "./logger";

/** Newest first: the running session, then the previous one, then the one before. */
export const LOG_FILE_NAMES = ["hibiki.log", "hibiki.1.log", "hibiki.2.log"] as const;

export const LOG_SOFT_LIMIT_BYTES = 3 * 1024 * 1024;
export const LOG_HARD_LIMIT_BYTES = 4 * 1024 * 1024;

/** Where each file goes on a new session start: [from, to], applied in order (oldest is dropped). */
export function logRotation(join: (name: string) => string): Array<[string, string]> {
  return [
    [join(LOG_FILE_NAMES[1]), join(LOG_FILE_NAMES[2])],
    [join(LOG_FILE_NAMES[0]), join(LOG_FILE_NAMES[1])],
  ];
}

/** A log sink that writes through `write` until the session's file reaches its limits. */
export function cappedLogSink(write: (text: string) => void): (line: string, level: LogLevel) => void {
  let written = 0;
  let state: "all" | "problems" | "stopped" = "all";
  const put = (text: string) => {
    written += text.length;
    write(text);
  };
  return (line, level) => {
    if (state === "stopped") return;
    if (state === "all" && written >= LOG_SOFT_LIMIT_BYTES) {
      state = "problems";
      put(`${new Date().toISOString()} WARN  [log] this session's log reached ${LOG_SOFT_LIMIT_BYTES / 1024 / 1024} MB; only warnings and errors are written from here\n`);
    }
    if (state === "problems") {
      if (level !== "warn" && level !== "error") return;
      if (written >= LOG_HARD_LIMIT_BYTES) {
        state = "stopped";
        put(`${new Date().toISOString()} WARN  [log] this session's log reached ${LOG_HARD_LIMIT_BYTES / 1024 / 1024} MB; nothing more is written\n`);
        return;
      }
    }
    put(line + "\n");
  };
}
