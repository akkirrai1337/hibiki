import { describe, expect, it } from "vitest";
import { buildMemorySnapshot, formatMemorySnapshot } from "./memoryDiagnostics";

describe("buildMemorySnapshot", () => {
  const metrics = [
    { pid: 1, type: "Browser", memory: { workingSetSize: 100 * 1024 } },
    { pid: 2, type: "Tab", memory: { workingSetSize: 300 * 1024 } },
    { pid: 3, type: "Tab", memory: { workingSetSize: 50 * 1024 } },
    { pid: 4, type: "Utility", serviceName: "Network Service", memory: { workingSetSize: 20 * 1024 } },
  ];
  const urls = new Map([[2, "file:///app/index.html#/"], [3, "https://kaa.lt/embed/x?token=secret#a"]]);

  it("sorts by size, totals, and keeps only the origin and path of a page", () => {
    const snapshot = buildMemorySnapshot(metrics, urls, 40 * 1024 * 1024, 5);
    expect(snapshot.processes.map((p) => p.pid)).toEqual([2, 1, 3, 4]);
    expect(snapshot.totalMb).toBe(470);
    expect(snapshot.mainHeapUsedMb).toBe(40);
    expect(snapshot.rendererCount).toBe(2);
    expect(snapshot.processes[0].url).toBe("app");
    expect(snapshot.processes[2].url).toBe("https://kaa.lt/embed/x");
    expect(snapshot.processes[3].name).toBe("Network Service");
  });

  it("formats one line per process", () => {
    const text = formatMemorySnapshot(buildMemorySnapshot(metrics, urls, 0, 5));
    expect(text.split("\n")).toHaveLength(5);
    expect(text).toContain("Tab pid=3 50 MB https://kaa.lt/embed/x");
  });
});
