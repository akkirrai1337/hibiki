import { app, webContents } from "electron";
import type { MemoryProcess, MemorySnapshot } from "@shared/types";

interface RawMetric {
  pid: number;
  type: string;
  name?: string;
  serviceName?: string;
  memory: { workingSetSize: number };
}

const toMb = (kilobytes: number): number => Math.round((kilobytes / 1024) * 10) / 10;

/** A page URL without its query or hash: enough to tell the app window from a hidden resolver
 * window, without dragging tokens and long query strings into a log file. */
function shortUrl(url: string): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "file:" || parsed.hostname === "localhost" ? "app" : `${parsed.origin}${parsed.pathname}`;
  } catch {
    return null;
  }
}

export function buildMemorySnapshot(metrics: RawMetric[], urlByPid: Map<number, string>, heapUsedBytes: number, now = Date.now()): MemorySnapshot {
  const processes: MemoryProcess[] = metrics
    .map((metric) => ({
      pid: metric.pid,
      type: metric.type,
      name: metric.name || metric.serviceName || null,
      url: metric.type === "Tab" ? shortUrl(urlByPid.get(metric.pid) ?? "") : null,
      workingSetMb: toMb(metric.memory.workingSetSize),
    }))
    .sort((a, b) => b.workingSetMb - a.workingSetMb);
  return {
    time: now,
    processes,
    totalMb: Math.round(processes.reduce((sum, process) => sum + process.workingSetMb, 0) * 10) / 10,
    mainHeapUsedMb: Math.round(heapUsedBytes / 1024 / 1024),
    rendererCount: processes.filter((process) => process.type === "Tab").length,
  };
}

export function collectMemorySnapshot(): MemorySnapshot {
  const urlByPid = new Map<number, string>();
  for (const contents of webContents.getAllWebContents()) {
    if (contents.isDestroyed()) continue;
    urlByPid.set(contents.getOSProcessId(), contents.getURL());
  }
  return buildMemorySnapshot(app.getAppMetrics() as RawMetric[], urlByPid, process.memoryUsage().heapUsed);
}

/** One line per process, for pasting into a bug report or the exported log. */
export function formatMemorySnapshot(snapshot: MemorySnapshot): string {
  const lines = snapshot.processes.map((process) => {
    const what = process.url ?? process.name ?? "";
    return `${process.type} pid=${process.pid} ${process.workingSetMb} MB${what ? ` ${what}` : ""}`;
  });
  return `total ${snapshot.totalMb} MB, main heap ${snapshot.mainHeapUsedMb} MB, ${snapshot.rendererCount} renderers\n${lines.join("\n")}`;
}
