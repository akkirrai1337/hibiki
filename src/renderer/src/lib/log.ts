// Renderer side of the app log (see core/logger.ts). Everything written here lands in the same
// single stream as main's own lines, in real time order, which is the whole point: an episode that
// fails to play produces a resolver line in main and a media-error line here, and reading them
// interleaved is what tells you whether the stream URL was never found or was found and then
// rejected by the player.
//
// Still mirrored to the devtools console - this replaces nothing during development, it only makes
// the same information survive past the session.
import { hibiki } from "./hibiki";

type Level = "debug" | "info" | "warn" | "error";

function send(level: Level, scope: string, message: string): void {
  try {
    hibiki.logs.append(level, scope, message);
  } catch {
    // Preload missing (a plain-browser render in tests): the console line below still happens.
  }
}

function format(parts: unknown[]): string {
  return parts
    .map((part) => {
      if (typeof part === "string") return part;
      if (part instanceof Error) return `${part.name}: ${part.message}`;
      try {
        return JSON.stringify(part);
      } catch {
        return String(part);
      }
    })
    .join(" ");
}

export const log = {
  debug: (scope: string, ...parts: unknown[]) => { send("debug", scope, format(parts)); },
  info: (scope: string, ...parts: unknown[]) => { send("info", scope, format(parts)); },
  warn: (scope: string, ...parts: unknown[]) => { console.warn(`[${scope}]`, ...parts); send("warn", scope, format(parts)); },
  error: (scope: string, ...parts: unknown[]) => { console.error(`[${scope}]`, ...parts); send("error", scope, format(parts)); },
};

/** Installed once from the app root: an uncaught renderer error or rejected promise is otherwise
 * invisible in an exported log, which makes a white-screen report unanswerable. */
export function installGlobalErrorLogging(): void {
  window.addEventListener("error", (event) => {
    log.error("window", event.message, event.filename ? `${event.filename}:${event.lineno}` : "");
  });
  window.addEventListener("unhandledrejection", (event) => {
    const reason = event.reason;
    log.error("window", `unhandled rejection: ${reason instanceof Error ? (reason.stack ?? reason.message) : String(reason)}`);
  });
}
