// App-wide diagnostic log, kept both in memory (a ring buffer, so "export log" is instant and
// never depends on disk state) and handed line by line to a platform sink - on desktop a rotating
// file under userData/logs (see main/logFile.ts), so a crash or a hard kill still leaves behind
// whatever was written before it.
//
// The reason this exists: the failures worth reporting here ("Kodik sometimes doesn't load, or
// takes ages") are intermittent, network-shaped and happen on the *user's* machine - by the time
// they're described to anyone the DevTools console is long gone. Every extension HTTP request,
// every resolver attempt and every player-link resolution now records a timed line, which makes
// the difference between "a mirror 404'd instantly" and "the TCP connect hung for 15 seconds"
// visible after the fact instead of only while attached to a debugger.
export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogEntry {
  time: number;
  level: LogLevel;
  scope: string;
  message: string;
}

// Enough to cover a full app session's worth of interesting activity (a single episode's link
// resolution is ~20 lines) without letting a runaway loop grow the process's memory unbounded.
const MAX_ENTRIES = 5000;

const entries: LogEntry[] = [];
let sink: ((line: string, level: LogLevel) => void) | null = null;
let minLevel: LogLevel = "debug";

const LEVEL_ORDER: Record<LogLevel, number> = { debug: 0, info: 1, warn: 2, error: 3 };

export function formatEntry(entry: LogEntry): string {
  return `${new Date(entry.time).toISOString()} ${entry.level.toUpperCase().padEnd(5)} [${entry.scope}] ${entry.message}`;
}

/** Where every formatted line goes besides the in-memory buffer, e.g. a log file. */
export function setLogSink(next: ((line: string, level: LogLevel) => void) | null): void {
  sink = next;
}

export function setLogLevel(level: LogLevel): void {
  minLevel = level;
}

export function log(level: LogLevel, scope: string, message: string): void {
  if (LEVEL_ORDER[level] < LEVEL_ORDER[minLevel]) return;
  const entry: LogEntry = { time: Date.now(), level, scope, message };
  entries.push(entry);
  if (entries.length > MAX_ENTRIES) entries.splice(0, entries.length - MAX_ENTRIES);
  const line = formatEntry(entry);
  sink?.(line, level);
  if (level === "error") console.error(line);
  else if (level === "warn") console.warn(line);
}

export const logger = {
  debug: (scope: string, message: string) => log("debug", scope, message),
  info: (scope: string, message: string) => log("info", scope, message),
  warn: (scope: string, message: string) => log("warn", scope, message),
  error: (scope: string, message: string) => log("error", scope, message),
};

export function recentEntries(limit = MAX_ENTRIES): LogEntry[] {
  return entries.slice(-limit);
}

/** The whole in-memory log as one plain-text document, newest last - what "export log" writes. */
export function renderLog(header: Record<string, string> = {}): string {
  const head = Object.entries(header).map(([key, value]) => `# ${key}: ${value}`);
  return [...head, "", ...entries.map(formatEntry), ""].join("\n");
}
