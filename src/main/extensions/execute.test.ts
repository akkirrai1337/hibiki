import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { executeExtensionCall } from "./execute";
import { createCallStorage } from "@shared/extensionCallStorage";

// A source's module-scope variables are its per-instance cache (animepahe's `cachedSession`,
// filter definitions, per-title lookups) - they have to survive from one call to the next on the
// same worker, and reset only when the script itself changes on disk.
describe("executeExtensionCall context persistence", () => {
  let dir: string;
  const write = (body: string) => {
    fs.writeFileSync(path.join(dir, "demo.manifest.json"), JSON.stringify({ id: "demo", name: "Demo", version: "1" }));
    fs.writeFileSync(path.join(dir, "demo.js"), body);
  };
  const call = (storageSnapshot?: Record<string, string>) =>
    executeExtensionCall(
      { extensionsDir: dir, sourceId: "demo", method: "getSettings", args: [] },
      {},
      createCallStorage(storageSnapshot).binding,
    ) as { count: number; stored: string | null };

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-exec-"));
  });
  afterEach(() => {
    fs.rmSync(dir, { recursive: true, force: true });
  });

  it("keeps module-scope state between calls", () => {
    write(`var count = 0; var Provider = { getSettings: function () { count += 1; return { count: count, stored: storage.get("k") }; } };`);
    expect(call().count).toBe(1);
    expect(call().count).toBe(2);
  });

  it("gives each call its own storage snapshot, not the first call's", () => {
    write(`var Provider = { getSettings: function () { return { count: 0, stored: storage.get("k") }; } };`);
    expect(call({ k: "first" }).stored).toBe("first");
    expect(call({ k: "second" }).stored).toBe("second");
    expect(call().stored).toBeNull();
  });

  it("rebuilds the context when the script changes on disk", () => {
    write(`var count = 0; var Provider = { getSettings: function () { count += 1; return { count: count }; } };`);
    expect(call().count).toBe(1);
    expect(call().count).toBe(2);
    write(`var count = 100; var Provider = { getSettings: function () { count += 1; return { count: count }; } };`);
    expect(call().count).toBe(101);
  });
});
