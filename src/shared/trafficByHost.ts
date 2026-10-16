// Bytes downloaded per host, for the log: says which source (or CDN) the traffic actually goes to,
// so the next optimisation is aimed by numbers, not guesses. Used by core (source requests) and by
// the renderer (video segments) alike; each keeps its own tally and writes it every few minutes,
// only when it has grown.

const INTERVAL_MS = 5 * 60 * 1000;
const TOP = 8;

function hostOf(url: string): string {
  try {
    return new URL(url).hostname || "?";
  } catch {
    return "?";
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export interface HostTally {
  add(url: string, bytes: number): void;
  /** "host 12.3 MB, host 1.1 MB, ... (+N more)", biggest first. */
  summary(): string;
}

/** A per-host tally that writes `label: summary` through `write` every 5 minutes while it grows. */
export function createHostTally(label: string, write: (line: string) => void): HostTally {
  const bytesByHost = new Map<string, number>();
  let total = 0;
  let written = 0;
  let timer: ReturnType<typeof setInterval> | null = null;

  const summary = () => {
    const sorted = [...bytesByHost.entries()].sort((a, b) => b[1] - a[1]);
    const shown = sorted.slice(0, TOP).map(([host, bytes]) => `${host} ${formatBytes(bytes)}`);
    const rest = sorted.length - shown.length;
    return `${formatBytes(total)} - ${shown.join(", ")}${rest > 0 ? ` (+${rest} more)` : ""}`;
  };

  return {
    add(url, bytes) {
      if (!(bytes > 0)) return;
      const host = hostOf(url);
      bytesByHost.set(host, (bytesByHost.get(host) ?? 0) + bytes);
      total += bytes;
      if (!timer) {
        timer = setInterval(() => {
          if (total === written) return;
          written = total;
          write(`${label} this session: ${summary()}`);
        }, INTERVAL_MS);
        (timer as { unref?: () => void }).unref?.();
      }
    },
    summary,
  };
}
